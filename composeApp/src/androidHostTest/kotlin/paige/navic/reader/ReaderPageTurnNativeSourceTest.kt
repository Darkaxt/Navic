package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.ui.screens.reader.ReaderPageTurnPrewarmRetryBudget
import paige.navic.ui.screens.reader.readerPageTurnCanStartPassivePrewarm
import paige.navic.ui.screens.reader.readerPageTurnRemainingAnimationDuration
import paige.navic.ui.screens.reader.readerPageTurnPixelsContainForeground

class ReaderPageTurnNativeSourceTest {
	@Test
	fun liveDragUsesPhysicalActiveLeafWidthAfterHalfResolutionCapture() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val pageAxisWidth = controller
			.substringAfter("private fun pageAxisWidth(viewWidth: Int): Int =")
			.substringBefore("private fun setGestureY(")

		assertContains(pageAxisWidth, "activeLeafRect(state.direction)")
		assertContains(pageAxisWidth, "leaf.width * transition.renderScaleX")
		assertContains(pageAxisWidth, "activeLeafAxisWidth")
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
	fun halfResolutionSnapshotsAreScaledOnlyAtTheCanvasBoundary() {
		val bitmapSource = readerAndroidFile("ReaderPageTurnBitmapSource.android.kt").readText()
		val bundleSource = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val bundle = readerAndroidFile("ReaderPageTurnBundle.android.kt").readText()
		val renderer = readerAndroidFile("ReaderPageTurnSlideView.android.kt").readText()

		assertContains(bundleSource, "private const val ReaderPageTurnAnimationBitmapScale = 0.5f")
		assertContains(bundleSource, "internal fun readerPageTurnAnimationBitmapDimension")
		assertContains(bitmapSource, "readerPageTurnAnimationBitmapDimension(pixelRect.width)")
		assertContains(bitmapSource, "readerPageTurnAnimationBitmapDimension(pixelRect.height)")
		assertContains(bundleSource, "readerPageTurnAnimationBitmapDimension(sourceRectInWindow.width())")
		assertContains(bundleSource, "readerPageTurnAnimationBitmapDimension(sourceRectInWindow.height())")
		assertContains(bundle, "val renderScaleX")
		assertContains(bundle, "val renderScaleY")
		assertContains(renderer, "canvas.scale(transition.renderScaleX, transition.renderScaleY)")
		assertFalse(renderer.contains("edgeOriginY"))
		assertFalse(renderer.contains("pointerY"))
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
			.substringAfter("private fun capturePreparedPrewarmWhenSettled(")
			.substringBefore("private fun isPrewarmActive(")

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
	fun rendererUsesRealSourceAndDestinationSnapshotsAndCompletesAnimations() {
		val renderer = readerAndroidFile("ReaderPageTurnSlideView.android.kt").readText()
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		assertContains(renderer, "transition.source.bitmap")
		assertContains(renderer, "transition.destination.bitmap")
		assertContains(renderer, "drawForward")
		assertContains(renderer, "drawBackward")
		assertContains(renderer, "drawMovingEdge")
		assertContains(renderer, "canvas.drawVertices(")
		assertFalse(renderer.contains("drawBitmapMesh"))
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
		assertContains(controller, "leaf.width * transition.renderScaleX")
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
		assertContains(controller, "pollNativePageTurnSettle")
		assertContains(controller, "detachAfterNavigationFrame()")
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
		val bundle = readerAndroidFile("ReaderPageTurnBundle.android.kt").readText()
		val renderer = readerAndroidFile("ReaderPageCurlGlRenderer.android.kt").readText()

		assertContains(controller, "FrameLayout.LayoutParams.MATCH_PARENT")
		assertContains(controller, "view.setTransition(transition, state.direction, left, top)")
		assertContains(bundle, "surfaceLeft = surfaceLeft.toFloat()")
		assertContains(bundle, "surfaceTop = surfaceTop.toFloat()")
		assertContains(controller, "ReaderPageTurnEffect.ShowFinalBase")
		assertContains(controller, "showFinalBase")
		assertFalse(controller.contains("setDestinationSettled()"))
		assertContains(renderer, "textureSet.surfaceLeft")
		assertContains(renderer, "textureSet.surfaceTop")
		assertContains(renderer, "GLES20.glUniform4f(rectUniform")
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
			.substringBefore("private fun beginTerminalAnimation(")
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
	fun landscapeControllerPreparesAndCommitsOneExactDestinationBundle() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()

		assertContains(controller, "ReaderPageTurnBundleSource")
		assertContains(controller, "pageTurnTransitionPlan")
		assertContains(controller, "beginPageTurnPreviewPreparation")
		assertContains(controller, "captureCurrentSurface")
		assertContains(controller, "captureBundle")
		assertContains(controller, "plan.targetPageIndex")
		assertContains(controller, "dispatchExactSettlement")
		assertContains(controller, "type: 'goToVisualPage'")
		assertContains(controller, "pageIndex: ${'$'}pageIndex")
		assertFalse(controller.contains("bitmapSource.capturePage(webView, direction)"))
	}

	@Test
	fun passivePrewarmReusesTheCachedSourceAndCapturesOnlyThePreparedDestination() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val prewarm = controller
			.substringAfter("private fun captureCachedPrewarm(")
			.substringBefore("private fun seedInitialPrewarmSnapshot(")
		val gesture = controller
			.substringAfter("private fun prepareBundle(")
			.substringBefore("private fun waitForPreviewReady(")

		val destinationCapture = prewarm.indexOf("capturePreparedBundle")
		assertTrue(destinationCapture >= 0, "Rolling prewarm must capture the prepared passive destination")
		assertFalse(
			prewarm.contains("captureCurrentSurface"),
			"Rolling prewarm must reuse its cached source instead of PixelCopying the live WebView"
		)

		val gestureCapture = gesture.indexOf("captureCurrentSurface")
		val gesturePreview = gesture.indexOf("beginPageTurnPreviewPreparation")
		assertTrue(gestureCapture >= 0 && gestureCapture < gesturePreview)
	}

	@Test
	fun initialPrewarmSeedsTheCurrentVisualSnapshotExactlyOnceAfterCacheReset() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val dispatcher = controller
			.substringAfter("private fun capturePreparedPrewarm(")
			.substringBefore("private fun captureCachedPrewarm(")
		val seed = controller
			.substringAfter("private fun seedInitialPrewarmSnapshot(")
			.substringBefore("private fun completePreparedPrewarm(")

		assertContains(source, "fun hasCachedSnapshot(")
		assertContains(dispatcher, "bundleSource.hasCachedSnapshot(plan.sourcePageIndex, plan.kind)")
		assertContains(dispatcher, "bundleSource.hasCachedSnapshot(visualPageIndex, plan.kind)")
		assertContains(dispatcher, "plan.sourcePageIndex == visualPageIndex")
		assertContains(dispatcher, "captureCachedPrewarm(")
		assertContains(dispatcher, "seedInitialPrewarmSnapshot(")
		assertFalse(dispatcher.contains("captureCurrentSurface"))

		assertEquals(1, Regex("bundleSource\\.captureCurrentSurface\\(").findAll(seed).count())
		assertContains(seed, "currentPageIndex = visualPageIndex")
		assertContains(seed, "currentCanRepresentSource = true")
		assertContains(seed, "bundleSource.captureBundle(")
	}

	@Test
	fun destinationRendererComposesTwoSnapshotsAndKeepsFinalBaseOpaque() {
		val renderer = readerAndroidFile("ReaderPageTurnSlideView.android.kt").readText()

		assertContains(renderer, "fun setTransition(")
		assertContains(renderer, "transition.source.bitmap")
		assertContains(renderer, "transition.destination.bitmap")
		assertContains(renderer, "if (showFinalBase || progress >= 1f)")
		assertContains(renderer, "canvas.drawBitmap(destination, 0f, 0f, bitmapPaint)")
		assertFalse(renderer.contains("turningReverse"))
		assertFalse(renderer.contains("underneath"))
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
	fun portraitRendererUsesTheSameDirectionalFrontWaveContract() {
		val renderer = readerAndroidFile("ReaderPageTurnSlideView.android.kt").readText()

		assertContains(renderer, "drawForward")
		assertContains(renderer, "drawBackward")
		assertContains(renderer, "ReaderPageTurnWaveGeometry")
		assertContains(renderer, "drawActiveLeaf(")
		assertFalse(renderer.contains("canvas.translate(-width * progress, 0f)"))
		assertFalse(renderer.contains("canvas.translate(-width + width * progress, 0f)"))
		assertFalse(renderer.contains("Camera"))
	}

	@Test
	fun controllerNormalizesDragProgressAgainstTheResolvedActiveLeaf() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val axisWidth = controller.substringAfter("private fun pageAxisWidth(").substringBefore("private fun setGestureY(")

		assertContains(controller, "activeLeafAxisWidth")
		assertContains(controller, "state.rebaseAxisSize(")
		assertContains(axisWidth, "activeLeafRect(state.direction")
		assertFalse(axisWidth.contains("transition.source.bitmap.width"))
	}

	@Test
	fun snapshotWavePathDoesNotCaptureReverseOrUnderneathSurfaces() {
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
	fun adjacentPrewarmWaitsUntilTheQueuedVisualCommitIsApplied() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val prewarm = controller
			.substringAfter("fun prewarmAdjacent()")
			.substringBefore("private fun queryAdjacentPrewarmPlans")
		val effects = controller
			.substringAfter("private fun handleEffects(")
			.substringBefore("private fun commitTurn(")
		val commit = controller
			.substringAfter("private fun commitTurn(")
			.substringBefore("private fun settleExactVisualPage(")

		assertContains(controller, "private var visualCommitPending = false")
		assertContains(prewarm, "visualCommitPending")
		assertContains(effects, "visualCommitPending = true")
		assertContains(commit, "visualCommitPending = false")
	}

	@Test
	fun stableFramePrewarmGateRequestsItsOwnSecondFrame() {
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val prewarm = host
			.substringAfter("private fun requestPageTurnPrewarmWhenReady()")
			.substringBefore("private fun pageTurnPrewarmLayoutSignature")

		assertContains(
			prewarm,
			"if (pageTurnPrewarmStableFrameCount < PageTurnPrewarmRequiredStableFrames) {"
		)
		assertContains(prewarm, "postInvalidateOnAnimation()")
		assertContains(prewarm, "viewTreeObserver.addOnPreDrawListener(listener)")
		assertTrue(
			prewarm.lastIndexOf("postInvalidateOnAnimation()") >
				prewarm.indexOf("viewTreeObserver.addOnPreDrawListener(listener)"),
			"Registering the stable-frame listener must schedule the first observed frame"
		)
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
			.substringAfter("private fun captureCompositedSurface(")
			.substringBefore("fun invalidatePage(")

		val opaqueFill = capture.indexOf("canvas.drawColor(backgroundColor)")
		val webViewDraw = capture.indexOf("webView.draw(canvas)")
		assertTrue(opaqueFill >= 0, "A transparent ARGB destination bitmap must be filled with opaque paper first.")
		assertTrue(webViewDraw > opaqueFill, "The WebView must be composited over the opaque paper fill.")
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
		assertContains(controller, "ComponentCallbacks2")
		assertContains(controller, "invalidate(\"memory-pressure\")")
		assertContains(controller, "unregisterComponentCallbacks")
	}

	@Test
	fun passiveSnapshotsWarmWhileTheShellCoverHidesTheReader() {
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val shellVisibility = host
			.substringAfter("fun setShellCoverVisible(visible: Boolean)")
			.substringBefore("private fun requestPageTurnPrewarmWhenReady()")
		val prewarm = host
			.substringAfter("private fun requestPageTurnPrewarmWhenReady()")
			.substringBefore("private fun pageTurnPrewarmLayoutSignature")

		assertContains(shellVisibility, "pageTurnController.invalidate(\"shell-cover-visible\")")
		assertContains(shellVisibility, "requestPageTurnPrewarmWhenReady()")
		assertFalse(prewarm.contains("shellCoverView?.visibility == VISIBLE"))
	}

	@Test
	fun shellCoverDismissalEvictsOnlyTheOccludedCurrentSnapshotBeforeRewarming() {
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val shellVisibility = host
			.substringAfter("fun setShellCoverVisible(visible: Boolean)")
			.substringBefore("private fun requestPageTurnPrewarmWhenReady()")
		val currentSnapshotInvalidation = controller
			.substringAfter("fun invalidateCurrentVisualSnapshot(reason: String)")
			.substringBefore("fun synchronizeVisualPageIndex(")
		val pageInvalidation = source
			.substringAfter("fun invalidatePage(pageIndex: Int, reason: String)")
			.substringBefore("fun invalidate(reason: String)")

		assertContains(shellVisibility, "pageTurnController.invalidateCurrentVisualSnapshot(\"shell-cover-hidden\")")
		assertTrue(
			shellVisibility.indexOf("pageTurnController.invalidateCurrentVisualSnapshot(\"shell-cover-hidden\")") <
				shellVisibility.lastIndexOf("requestPageTurnPrewarmWhenReady()"),
			"The covered source must be evicted before the uncovered page is prewarmed."
		)
		assertContains(currentSnapshotInvalidation, "cancelPrewarm()")
		assertContains(currentSnapshotInvalidation, "bundleSource.invalidatePage(pageIndex, reason)")
		assertContains(currentSnapshotInvalidation, "bundleSource.invalidate(reason)")
		assertContains(pageInvalidation, "key.visualPageIndex == pageIndex")
		assertContains(pageInvalidation, "releaseCacheOwnership()")
		assertFalse(pageInvalidation.contains("snapshotCache.clear()"))
	}

	@Test
	fun backgroundPrewarmShieldsWithTheCachedVisualPageInsteadOfADistantPlanSource() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val prewarmCapture = controller
			.substringAfter("private fun captureCachedPrewarm(")
			.substringAfter("bundleSource.capturePreparedBundle(")
			.substringBefore(") { transition ->")
		val bundleCapture = source
			.substringAfter("fun capturePreparedBundle(")
			.substringBefore("fun cacheCurrentSnapshot(")

		assertContains(prewarmCapture, "shieldPageIndex = visualPageIndex")
		assertContains(bundleCapture, "cachedSnapshot(shieldPageIndex, plan.kind)")
		assertContains(bundleCapture, "onStagingStarted(shield)")
		assertFalse(
			bundleCapture.contains("onStagingStarted = { onStagingStarted(source) }"),
			"Prewarming a distant target must not replace the visible reader with that plan's stale source page."
		)
	}

	@Test
	fun authoritativeReaderLocationReanchorsTheNativeSlideCoordinator() {
		val platform = readerCommonUiFile("ReaderPlatformHosts.kt").readText()
		val root = readerCommonUiFile("ReaderRoot.kt").readText()
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()

		assertContains(platform, "pageTurnVisualPageIndex: Int?")
		assertContains(root, "pageTurnVisualPageIndex = controllerState.chrome.currentLocator?.pageIndex")
		assertContains(platform, "pageTurnVisualLocationReason: String?")
		assertContains(root, "pageTurnVisualLocationReason = controllerState.chrome.currentLocator?.reason")
		assertContains(host, "setPageTurnVisualLocation(pageTurnVisualPageIndex, pageTurnVisualLocationReason)")
		assertContains(host, "pageTurnController.synchronizeVisualPageIndex(normalized, reason)")
		assertContains(controller, "fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?)")
		assertContains(controller, "slideCoordinator?.visualPageIndex == pageIndex")
		assertContains(controller, "ReaderPageSlideCoordinator(pageIndex)")
	}

	@Test
	fun exactLocationEventsCompleteSettlementWithoutPollingTheWebView() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val synchronize = controller
			.substringAfter("fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?)")
			.substringBefore("private fun begin(")
		val dispatch = controller
			.substringAfter("private fun dispatchExactSettlement(")
			.substringBefore("private fun pollNativePageTurnSettle(")

		assertContains(synchronize, "reason == \"page-turn:exact\"")
		assertContains(synchronize, "coordinator.activeSettlementTarget == pageIndex")
		assertContains(synchronize, "coordinator.settlementReported(")
		assertFalse(synchronize.contains("pageIndex != coordinator.visualPageIndex"))
		assertContains(synchronize, "onRequestPrewarm()")
		assertFalse(controller.contains("pollExactPageTurnSettle"))
		assertFalse(dispatch.contains("postOnAnimation"))
		assertTrue(
			synchronize.indexOf("coordinator.settlementReported(") <
				synchronize.indexOf("bundleSource.invalidate(\"external-page-relocation\")"),
			"An expected exact settlement must advance the coordinator before external relocation handling"
		)
	}

	@Test
	fun passiveAdjacentPrewarmMayStartDuringExactSettlement() {
		assertTrue(
			readerPageTurnCanStartPassivePrewarm(
				destroyed = false,
				available = true,
				visualCommitPending = false,
				idle = true
			)
		)
		assertFalse(
			readerPageTurnCanStartPassivePrewarm(
				destroyed = false,
				available = true,
				visualCommitPending = true,
				idle = true
			)
		)

		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val prewarm = controller
			.substringAfter("fun prewarmAdjacent(): Boolean")
			.substringBefore("private fun queryAdjacentPrewarmPlans")

		assertFalse(prewarm.contains("slideCoordinator?.activeSettlementTarget != null"))
		assertContains(prewarm, "readerPageTurnCanStartPassivePrewarm")
	}

	@Test
	fun preparedAdjacentCaptureUsesThePassiveRendererDuringExactSettlement() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val preparedCapture = controller
			.substringAfter("private fun captureCachedPrewarm(")
			.substringBefore("private fun seedInitialPrewarmSnapshot(")

		assertContains(preparedCapture, "bundleSource.capturePreparedBundle")
		assertFalse(preparedCapture.contains("activeSettlementTarget"))
		assertFalse(preparedCapture.contains("bundleSource.captureCurrentSurface"))
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
