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
		val captureCallback = controller.substringAfter("captureCurrentSurface")
		val validationIndex = captureCallback.indexOf("isPreparationActive(plan, stateGeneration)")
		val assignmentIndex = captureCallback.indexOf("activeBundle = bundle")

		assertTrue(validationIndex >= 0, "The controller must validate the preparation generation first.")
		assertTrue(
			validationIndex < assignmentIndex,
			"A stale capture must not become the active capture result."
		)
		assertContains(captureCallback, "current.bitmap.takeUnless { it.isRecycled }?.recycle()")
	}

	@Test
	fun stalePreparationFailureCannotDisableCanvasForTheSession() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val failureBranch = controller.substringAfter("private fun failPreparation(").substringBefore("\n\t}")

		assertContains(failureBranch, "if (state.captureFailed(stateGeneration))")
		assertFalse(failureBranch.contains("enabledForSession = false"))
	}

	@Test
	fun rendererUsesRealDestinationSurfacesAndCompletesAnimations() {
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		assertContains(renderer, "reverseFaceColor")
		assertContains(renderer, "bundle.underneath")
		assertContains(renderer, "bundle.turningReverse")
		assertTrue(
			renderer.indexOf("canvas.drawBitmapMesh") < renderer.indexOf("drawReverseFace(canvas"),
			"The real reverse face must cover the folded-away front pixels instead of sitting behind them."
		)
		assertFalse(renderer.contains("underlayPaint"))
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
		assertContains(controller, "activeBundle?.turningFront?.width")
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
		assertContains(controller, "surfaceLeft = left")
		assertContains(controller, "surfaceTop = top")
		assertContains(controller, "ReaderPageTurnEffect.ShowFinalBase")
		assertContains(controller, "showFinalBase")
		assertFalse(controller.contains("curlView?.setDestinationSettled()"))
		assertContains(renderer, "surfaceLeft")
		assertContains(renderer, "surfaceTop")
		assertContains(renderer, "canvas.translate(surfaceLeft, surfaceTop)")
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

	@Test
	fun landscapeControllerPreparesAndCommitsOneExactDestinationBundle() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()

		assertContains(controller, "ReaderPageTurnBundleSource")
		assertContains(controller, "pageTurnTransitionPlan")
		assertContains(controller, "beginPageTurnPreviewPreparation")
		assertContains(controller, "captureCurrentSurface")
		assertContains(controller, "captureBundle")
		assertContains(controller, "setTargetPageIndex")
		assertContains(controller, "plan.targetPageIndex")
		assertContains(controller, "type: 'goToVisualPage'")
		assertContains(controller, "pageIndex: ${'$'}{plan.targetPageIndex}")
		assertFalse(controller.contains("bitmapSource.capturePage(webView, direction)"))
	}

	@Test
	fun destinationRendererComposesAllSurfacesAndKeepsFinalBaseOpaque() {
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()

		assertContains(renderer, "fun setBundle(")
		assertContains(renderer, "bundle.currentBase")
		assertContains(renderer, "bundle.underneath")
		assertContains(renderer, "bundle.turningFront")
		assertContains(renderer, "bundle.turningReverse")
		assertContains(renderer, "bundle.finalBase")
		assertTrue(
			renderer.indexOf("bundle.underneath") < renderer.indexOf("bundle.turningFront"),
			"The underneath page must be painted before the deforming front leaf."
		)
		assertContains(renderer, "if (showFinalBase)")
	}

	@Test
	fun reversePageShadingPreservesCapturedText() {
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()
		val reverse = renderer.substringAfter("private fun drawReverseFace(").substringBefore("\n\t}")

		assertContains(reverse, "canvas.drawBitmapMesh(reverse")
		assertContains(reverse, "Color.argb(")
		assertContains(reverse, "Color.TRANSPARENT")
		assertFalse(reverse.contains("intArrayOf(darken(reverseFaceColor"))
	}

	@Test
	fun portraitRendererUsesCameraSlideOnlyForSlidePlans() {
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()

		assertContains(renderer, "ReaderPageTurnTransitionKind.PortraitSlide")
		assertContains(renderer, "drawPortraitSlide")
		assertContains(renderer, "bundle.currentBase")
		assertContains(renderer, "bundle.finalBase")
		assertContains(renderer, "canvas.translate(currentOffset, 0f)")
		assertContains(renderer, "canvas.translate(targetOffset, 0f)")
	}

	@Test
	fun portraitLeafKeepsRealReverseAndDistinctUnderneathSurfaces() {
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertContains(renderer, "ReaderPageTurnTransitionKind.PortraitLeaf")
		assertContains(renderer, "bundle.turningReverse")
		assertContains(renderer, "bundle.underneath")
		assertContains(source, "underneathBase ?: finalBase")
	}
}
