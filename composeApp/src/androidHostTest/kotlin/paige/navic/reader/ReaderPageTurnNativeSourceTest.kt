package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.ui.screens.reader.ReaderPageTurnPrewarmRetryBudget
import paige.navic.ui.screens.reader.readerPageTurnRemainingAnimationDuration
import paige.navic.ui.screens.reader.readerPageTurnPixelsContainForeground

class ReaderPageTurnNativeSourceTest {
	@Test
	fun liveDragUsesPhysicalSurfaceWidthAfterHalfResolutionCapture() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val pageAxisWidth = controller
			.substringAfter("private fun pageAxisWidth(viewWidth: Int): Int =")
			.substringBefore("\n\n")

		assertContains(pageAxisWidth, "transition.source.bitmap.width * transition.renderScaleX")
		assertContains(pageAxisWidth, "viewWidth.coerceAtLeast(1)")
	}

	@Test
	fun commitAnimationDurationTracksOnlyTheUnfinishedFoldDistance() {
		assertTrue(readerPageTurnRemainingAnimationDuration(0f, 1f, 350L) == 350L)
		assertTrue(readerPageTurnRemainingAnimationDuration(0.5f, 1f, 350L) == 175L)
		assertTrue(readerPageTurnRemainingAnimationDuration(1f, 1f, 350L) == 0L)
	}
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
	fun currentSurfaceCaptureWaitsForVisualStateAndRejectsUnpaintedPixels() {
		val source = readerAndroidFile("ReaderPageTurnBitmapSource.android.kt").readText()

		assertContains(source, "postVisualStateCallback")
		assertContains(source, "VisualStateCallback")
		assertContains(source, "containsRenderableForeground")
		assertContains(source, "Page-turn capture rejected unpainted surface")
	}

	@Test
	fun renderableForegroundRequiresVisibleContrastRatherThanPaperNoise() {
		val blankPaper = IntArray(48 * 32) { index ->
			val level = 248 + (index % 8)
			(0xff shl 24) or (level shl 16) or (level shl 8) or level
		}
		val textPage = blankPaper.copyOf().also { pixels ->
			for (index in 0 until pixels.size step 31) pixels[index] = 0xff202020.toInt()
		}
		val darkPage = IntArray(48 * 32) { 0xff181818.toInt() }.also { pixels ->
			for (index in 0 until pixels.size step 29) pixels[index] = 0xffeeeeee.toInt()
		}

		assertFalse(readerPageTurnPixelsContainForeground(blankPaper))
		assertTrue(readerPageTurnPixelsContainForeground(textPage))
		assertTrue(readerPageTurnPixelsContainForeground(darkPage))
	}

	@Test
	fun halfResolutionBundlesAreScaledOnlyAtTheCanvasBoundary() {
		val bitmapSource = readerAndroidFile("ReaderPageTurnBitmapSource.android.kt").readText()
		val bundleSource = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val bundle = readerAndroidFile("ReaderPageTurnBundle.android.kt").readText()
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()

		assertContains(bundleSource, "private const val ReaderPageTurnAnimationBitmapScale = 0.5f")
		assertContains(bundleSource, "internal fun readerPageTurnAnimationBitmapDimension")
		assertContains(bitmapSource, "readerPageTurnAnimationBitmapDimension(pixelRect.width)")
		assertContains(bitmapSource, "readerPageTurnAnimationBitmapDimension(pixelRect.height)")
		assertContains(bundleSource, "readerPageTurnAnimationBitmapDimension(sourceRectInWindow.width())")
		assertContains(bundleSource, "readerPageTurnAnimationBitmapDimension(sourceRectInWindow.height())")
		assertContains(bundle, "val renderScaleX")
		assertContains(bundle, "val renderScaleY")
		assertContains(renderer, "canvas.scale(bundle.renderScaleX, bundle.renderScaleY)")
		assertContains(renderer, "edgeOriginY / bundle.renderScaleY")
		assertContains(renderer, "pointerY / bundle.renderScaleY")
	}

	@Test
	fun prewarmRetryBudgetAllowsOneEventDrivenRetryPerAdjacentDirection() {
		val budget = ReaderPageTurnPrewarmRetryBudget()

		assertTrue(budget.consume("forward"))
		assertFalse(budget.consume("forward"))
		assertTrue(budget.consume("previous"))
		budget.clear()
		assertTrue(budget.consume("forward"))
	}

	@Test
	fun rejectedPrewarmCaptureReturnsToTheQueueWithoutAClockDelay() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val prewarm = controller
			.substringAfter("private fun prewarmNext(")
			.substringBefore("private fun waitForPrewarmPreviewReady(")

		assertContains(prewarm, "prewarmRetryBudget.consume(plan.cacheKey)")
		assertContains(prewarm, "prewarmPlans.addLast(encodedPlan)")
		assertFalse(prewarm.contains("postDelayed"))
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
		val assignmentIndex = captureCallback.indexOf("activeTransition = transition")

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
		val failureBranch = controller
			.substringAfter("private fun markPreparationUnavailable(")
			.substringBefore("private fun attachPreparationShield")

		assertContains(failureBranch, "activeStateGeneration != stateGeneration")
		assertFalse(failureBranch.contains("state.captureFailed"))
		assertFalse(failureBranch.contains("enabledForSession = false"))
	}

	@Test
	fun rendererUsesRealDestinationSurfacesAndCompletesAnimations() {
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val draw = renderer
			.substringAfter("override fun onDraw(canvas: Canvas)")
			.substringBefore("private fun drawPortraitSlide")
		assertContains(renderer, "reverseFaceColor")
		assertContains(renderer, "bundle.underneath")
		assertContains(renderer, "bundle.turningReverse")
		assertTrue(
			draw.indexOf("drawFrontFace(canvas, source)") < draw.indexOf("drawReverseFace(canvas"),
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
		assertContains(controller, "transition.source.bitmap.width * transition.renderScaleX")
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
		val renderer = readerAndroidFile("ReaderPageTurnSlideView.android.kt").readText()
		val stateMachine = readerCommonFile("ReaderPageTurnStateMachine.kt").readText()
		val animationFinished = stateMachine
			.substringAfter("fun animationFinished()")
			.substringBefore("fun destinationSettled(")
		val terminalAnimation = stateMachine
			.substringAfter("private fun beginTerminalAnimation(")
			.substringBefore("private fun finishCommitIfReady(")

		assertContains(controller, "CommitEndProgress = 1f")
		assertContains(controller, "readerPageTurnRemainingAnimationDuration(")
		assertContains(controller, "to = CommitEndProgress")
		assertContains(controller, "ReaderPageTurnEffect.ShowFinalBase")
		assertContains(renderer, "showFinalBase")
		assertContains(renderer, "progress.coerceIn(0f, 1f)")
		assertFalse(terminalAnimation.contains("ReaderPageTurnEffect.Commit(direction)"))
		assertTrue(
			animationFinished.indexOf("ReaderPageTurnEffect.ShowFinalBase") <
				animationFinished.indexOf("ReaderPageTurnEffect.Commit(direction)"),
			"The opaque final bitmap must be shown before live Foliate navigation starts"
		)
		assertContains(controller, "host.postOnAnimation { commitTurn(effect.direction) }")
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
		assertContains(controller, "dispatchExactSettlement")
		assertContains(controller, "type: 'goToVisualPage'")
		assertContains(controller, "pageIndex: ${'$'}pageIndex")
		assertFalse(controller.contains("bitmapSource.capturePage(webView, direction)"))
	}

	@Test
	fun liveSurfaceCapturePrecedesEveryDestinationPreviewMutation() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val prewarm = controller
			.substringAfter("private fun prewarmNext(")
			.substringBefore("private fun waitForPrewarmPreviewReady(")
		val gesture = controller
			.substringAfter("private fun prepareBundle(")
			.substringBefore("private fun waitForPreviewReady(")

		for (path in listOf(prewarm, gesture)) {
			val capture = path.indexOf("captureCurrentSurface")
			val preview = path.indexOf("beginPageTurnPreviewPreparation")
			assertTrue(capture >= 0, "Page-turn preparation must capture the live surface")
			assertTrue(preview >= 0, "Page-turn preparation must initialize the destination preview")
			assertTrue(
				capture < preview,
				"Destination preview mutation must not hide live pages before currentBase is captured"
			)
		}
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
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val reverse = renderer.substringAfter("private fun drawReverseFace(").substringBefore("\n\t}")

		assertContains(renderer, "private val reverseVertices")
		assertContains(renderer, "private val reverseColors")
		assertContains(renderer, "buildReverseMesh")
		assertContains(reverse, "canvas.drawBitmapMesh(reverse, MeshColumns, MeshRows, reverseVertices, 0, reverseColors")
		assertFalse(source.contains("mirroredHorizontally"), "Reverse-page capture must retain its reading orientation; geometry owns the fold transform.")
		assertContains(reverse, "Color.argb(")
		assertContains(reverse, "Color.TRANSPARENT")
		assertFalse(reverse.contains("intArrayOf(darken(reverseFaceColor"))
	}

	@Test
	fun frontFaceExcludesTheFoldedRegionBeforeReverseFaceIsPainted() {
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()
		val draw = renderer.substringAfter("override fun onDraw(canvas: Canvas)").substringBefore("private fun drawPortraitSlide")
		val front = renderer.substringAfter("private fun drawFrontFace(").substringBefore("private fun drawReverseFace(")

		assertTrue(
			draw.indexOf("buildFoldPaths(geometry)") < draw.indexOf("drawFrontFace(canvas, source)"),
			"The folded polygon must be known before front-face clipping"
		)
		assertTrue(
			draw.indexOf("drawFrontFace(canvas, source)") < draw.indexOf("drawReverseFace(canvas"),
			"The reverse face must cover the clipped front face"
		)
		assertContains(front, "clipOutPath(foldedRegionPath)")
		assertContains(front, "canvas.drawBitmapMesh(source")
	}

	@Test
	fun overlayAttachmentNeverEncodesDiagnosticBitmapsOnTheMainThread() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val attach = controller.substringAfter("private fun attachOverlay()").substringBefore("private fun applyGestureYToOverlay")

		assertFalse(attach.contains("Bitmap.compress"))
		assertFalse(attach.contains("page-turn-diagnostic"))
		assertFalse(attach.contains("dumpBundleForReaderdev"))
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
	fun flatSlidePathDoesNotCaptureReverseOrUnderneathSurfaces() {
		val renderer = readerAndroidFile("ReaderPageTurnSlideView.android.kt").readText()
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertFalse(renderer.contains("turningReverse"))
		assertFalse(renderer.contains("underneath"))
		assertFalse(source.contains("capturePortraitUnderneath"))
	}

	@Test
	fun adjacentSnapshotsPrewarmBeforeGestureWithoutTapOrRelativeFallback() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val prewarmMethod = host
			.substringAfter("private fun requestPageTurnPrewarmWhenReady()")
			.substringBefore("private fun removePageTurnPrewarmLayoutListener()")

		assertContains(controller, "fun prewarmAdjacent()")
		assertContains(controller, "ReaderPageTurnPhysicalDirection.TowardLeft")
		assertContains(controller, "ReaderPageTurnPhysicalDirection.TowardRight")
		assertContains(controller, "bundleSource.cached(plan)")
		assertContains(controller, "releasedWhilePreparing")
		assertContains(controller, "commitShieldedColdFallback")
		assertContains(controller, "type: 'goToVisualPage'")
		assertFalse(controller.contains("commitRelativeColdFallback"))
		assertContains(host, "requestPageTurnPrewarmWhenReady")
		assertContains(host, "ViewTreeObserver.OnPreDrawListener")
		assertContains(host, "pageTurnPrewarmStableFrameCount")
		assertContains(host, "PageTurnPrewarmRequiredStableFrames")
		assertContains(host, "if (pageTurnPrewarmStableFrameCount < PageTurnPrewarmRequiredStableFrames)")
		assertFalse(controller.contains("postDelayed"))
		assertFalse(prewarmMethod.contains("postDelayed"))
	}

	@Test
	fun releasedGestureConsumesWarmSnapshotOrWaitsForInFlightCapture() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val release = controller.substringAfter("fun release(").substringBefore("private fun pageAxisWidth")
		val callback = controller
			.substringAfter("val plan = ReaderPageTurnTransitionPlan.parse(encodedPlan, token, bundleGeneration)")
			.substringBefore("fun prewarmAdjacent()")
		val cacheLookup = callback.indexOf("bundleSource.cached(plan)")
		val coldPreparation = callback.indexOf("prepareBundle(webView, plan, activeStateGeneration)")

		assertTrue(cacheLookup >= 0, "Gesture planning must check the adjacent bundle cache")
		assertTrue(coldPreparation >= 0, "A cold gesture must continue preparing after release")
		assertTrue(
			cacheLookup < coldPreparation,
			"A released gesture must consume an already-warm snapshot before waiting for capture"
		)
		assertFalse(callback.contains("if (releasedWhilePreparing)"))
		assertContains(release, "releasedWhilePreparing && preparationUnavailable")
	}

	@Test
	fun prewarmRendererPersistsUntilExplicitLifecycleInvalidation() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()
		val finishPrewarm = controller
			.substringAfter("private fun finishPrewarm()")
			.substringBefore("private fun cancelPrewarm()")
		val cancelPrewarm = controller
			.substringAfter("private fun cancelPrewarm()")
			.substringBefore("private fun destroyPageTurnPreviewRenderer(")
		val destroy = controller
			.substringAfter("fun destroy()")
			.substringBefore("fun invalidate(")
		val invalidate = controller
			.substringAfter("fun invalidate(reason: String)")
			.substringBefore("private fun begin(")

		assertContains(controller, "onRequestPrewarm")
		assertFalse(finishPrewarm.contains("destroyPageTurnPreviewRenderer"))
		assertFalse(cancelPrewarm.contains("destroyPageTurnPreviewRenderer"))
		assertContains(destroy, "destroyPageTurnPreviewRenderer(\"controller-destroyed\")")
		assertContains(invalidate, "destroyPageTurnPreviewRenderer(reason)")
		assertFalse(controller.contains("postOnAnimation { prewarmAdjacent() }"))
		assertContains(source, "private const val MaxCachedSnapshots = 5")
		assertContains(preview, "previewView.close?.()")
		assertContains(preview, "previewView.remove?.()")
	}

	@Test
	fun unavailableCaptureFallsBackOnlyAfterReleaseAndOnlyBehindAShield() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val release = controller.substringAfter("fun release(").substringBefore("private fun pageAxisWidth")
		val resolve = controller.substringAfter("private fun resolveColdRelease()").substringBefore("private fun commitShieldedColdFallback")
		val unavailable = controller
			.substringAfter("private fun markPreparationUnavailable(")
			.substringBefore("private fun attachPreparationShield")

		assertContains(release, "releasedWhilePreparing && preparationUnavailable")
		assertContains(resolve, "preparationShieldSnapshot")
		assertContains(resolve, "commitShieldedColdFallback")
		assertFalse(resolve.contains("commitRelativeColdFallback"))
		assertContains(controller, "markPreparationUnavailable(activeStateGeneration, \"transition-plan-unavailable\")")
		assertContains(controller, "markPreparationUnavailable(stateGeneration, \"current-surface-unavailable\")")
		assertContains(controller, "markPreparationUnavailable(stateGeneration, \"destination-bundle-unavailable\")")
		assertFalse(unavailable.contains("state.captureFailed"))
		assertContains(unavailable, "preparationUnavailable = true")
		assertContains(unavailable, "releasedWhilePreparing")
		assertContains(unavailable, "resolveColdRelease()")
	}

	@Test
	fun passiveStagingIsHiddenByTheCurrentImmutableSurface() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertContains(source, "onStagingStarted")
		assertContains(controller, "attachPreparationShield")
		assertContains(controller, "removePreparationShield")
		assertContains(controller, "ImageView")
		assertContains(controller, "snapshot.bitmap")
	}

	@Test
	fun stagedDestinationCaptureIsOpaqueBeforeTheWebViewIsDrawn() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val capture = source
			.substringAfter("internal fun captureStagedSurface(")
			.substringBefore("private fun buildBundle(")

		val opaqueFill = capture.indexOf("canvas.drawColor(readerPageTurnOpaqueColor(geometry.reverseFaceColorArgb))")
		val webViewDraw = capture.indexOf("webView.draw(canvas)")
		assertTrue(opaqueFill >= 0, "A transparent ARGB destination bitmap must be filled with opaque paper first.")
		assertTrue(webViewDraw > opaqueFill, "The WebView must be composited over the opaque paper fill.")
	}

	@Test
	fun reverseLeafAlwaysPaintsOpaquePaperBelowItsCapturedContent() {
		val renderer = readerAndroidFile("ReaderPageTurnCurlView.android.kt").readText()
		val reverse = renderer
			.substringAfter("private fun drawReverseFace(")
			.substringBefore("private fun drawCrease(")

		val paperBacking = reverse.indexOf("canvas.drawPath(reversePath, reversePaint)")
		val capturedContent = reverse.indexOf("canvas.drawBitmapMesh(reverse")
		assertTrue(paperBacking >= 0, "The reverse face needs an opaque paper backing for mesh and capture gaps.")
		assertTrue(capturedContent > paperBacking, "Captured reverse-page content must be drawn over the paper backing.")
	}

	@Test
	fun previewCaptureWaitsForRestoredLiveCompositionBeforeCompleting() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val restore = source
			.substringAfter("private fun restoreLiveComposition(")
			.substringBefore("private fun put(")

		assertContains(restore, "onRestored: () -> Unit")
		assertContains(restore, "postVisualStateCallback")
		assertContains(restore, "postOnAnimation")
		assertContains(source, "restoreLiveComposition(webView, plan.token) {")
	}

	@Test
	fun pageTurnSnapshotsInvalidateAcrossSettingsLayoutLifecycleAndMemoryPressure() {
		val platform = readerCommonUiFile("ReaderPlatformHosts.kt").readText()
		val root = readerCommonUiFile("ReaderRoot.kt").readText()
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()

		assertContains(platform, "pageTurnSnapshotKey: Int")
		assertContains(root, "pageTurnSnapshotKey = controllerState.chrome.settings.hashCode()")
		assertContains(host, "setPageTurnSnapshotKey")
		assertContains(host, "pageTurnController.invalidate(\"settings-changed\")")
		assertContains(host, "pageTurnController.invalidate(\"size-changed\")")
		assertContains(host, "pageTurnController.invalidate(\"window-hidden\")")
		assertContains(host, "setShellCoverVisible(visible)")
		assertContains(host, "pageTurnController.invalidate(\"shell-cover-visible\")")
		assertContains(host, "if (shellCoverView?.visibility == VISIBLE) return")
		assertContains(controller, "ComponentCallbacks2")
		assertContains(controller, "invalidate(\"memory-pressure\")")
		assertContains(controller, "unregisterComponentCallbacks")
	}

	@Test
	fun rotationPrewarmWaitsForNativeAndJsLayoutModesToAgree() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val bundle = readerAndroidFile("ReaderPageTurnBundle.android.kt").readText()
		val query = controller
			.substringAfter("private fun queryAdjacentPrewarmPlans(")
			.substringBefore("private fun prewarmNext(")
		val begin = controller
			.substringAfter("private fun begin(deltaX: Float)")
			.substringBefore("fun prewarmAdjacent()")

		assertContains(bundle, "fun matchesLayout(spread: Boolean)")
		assertContains(query, "expectedLayoutMode(webView)")
		assertContains(query, "context?.optString(\"layoutMode\")")
		assertContains(query, "webView.postOnAnimation { queryAdjacentPrewarmPlans(webView, session) }")
		assertContains(begin, "!plan.matchesLayout(state.spread)")
	}
}
