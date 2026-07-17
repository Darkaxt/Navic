package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
	fun rasterSchedulerCanAwaitCaptureWithoutAClockOrCancellationTimeout() {
		val source = readerAndroidFile("ReaderPageTurnBitmapSource.android.kt").readText()
		val awaitCapture = source
			.substringAfter("suspend fun captureSurfaceAwait(")
			.substringBefore("private fun capture(")

		assertContains(awaitCapture, "suspendCancellableCoroutine")
		assertContains(awaitCapture, "continuation.isActive")
		assertContains(awaitCapture, "result?.bitmap?.takeUnless { it.isRecycled }?.recycle()")
		assertFalse(awaitCapture.contains("withTimeout"))
		assertFalse(awaitCapture.contains("postDelayed"))
	}

	@Test
	fun capturedTurnSnapshotsPublishThroughTheProfileAwareRasterScheduler() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()

		assertContains(source, "ReaderPageRasterScheduler<Bitmap>")
		assertContains(source, "readerPageRasterStorageRoot")
		assertContains(source, "pageTurnRasterDescriptor")
		assertContains(source, "readerPageRasterDescriptor")
		assertContains(source, "scheduler.activateProfile(key.profile)")
		assertContains(source, "schedulePersistentSnapshot")
		assertContains(source, "ReaderPageRasterPriority.Current")
		assertContains(source, "ReaderPageRasterPriority.NextTransition")
		assertFalse(source.contains("withTimeout"))
		assertFalse(source.contains("timeoutMillis"))
	}

	@Test
	fun bundleSourceOwnsAndCancelsItsRasterCoroutineLifetime() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val close = source
			.substringAfter("fun close()")
			.substringBefore("private fun restoreLiveComposition(")

		assertContains(source, "private val rasterJob = SupervisorJob()")
		assertContains(source, "CoroutineScope(rasterJob + Dispatchers.Main.immediate)")
		assertContains(close, "rasterScheduler?.close()")
		assertContains(close, "rasterJob.cancel()")
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
	fun configuredResolutionSnapshotsAreScaledOnlyAtTheRendererBoundary() {
		val bitmapSource = readerAndroidFile("ReaderPageTurnBitmapSource.android.kt").readText()
		val bundleSource = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val bundle = readerAndroidFile("ReaderPageTurnBundle.android.kt").readText()
		val renderer = readerAndroidFile("ReaderPageTurnSlideView.android.kt").readText()

		assertContains(bundleSource, "internal fun readerPageTurnAnimationBitmapDimension")
		assertContains(bitmapSource, "readerPageTurnAnimationBitmapDimension(pixelRect.width, bitmapQuality)")
		assertContains(bitmapSource, "readerPageTurnAnimationBitmapDimension(pixelRect.height, bitmapQuality)")
		assertContains(bundleSource, "readerPageTurnAnimationBitmapDimension(sourceRectInWindow.width(), bitmapQuality)")
		assertContains(bundleSource, "readerPageTurnAnimationBitmapDimension(sourceRectInWindow.height(), bitmapQuality)")
		assertContains(bundleSource, "bitmapQuality = bitmapQuality")
		assertContains(bundle, "val renderScaleX")
		assertContains(bundle, "val renderScaleY")
		assertContains(renderer, "canvas.scale(transition.renderScaleX, transition.renderScaleY)")
		assertFalse(renderer.contains("edgeOriginY"))
		assertFalse(renderer.contains("pointerY"))
	}

	@Test
	fun passivePrewarmHasOneBatchOwnerInsteadOfPerTransitionRetryState() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()

		assertContains(controller, "ReaderPageRasterBatchController(bundleSource)")
		assertFalse(controller.contains("ReaderPageTurnPrewarmRetryBudget"))
		assertFalse(controller.contains("prewarmPlans"))
		assertFalse(controller.contains("activePrewarmPlan"))
	}

	@Test
	fun cancelledPassiveBatchRestoresTheLiveReaderWithoutDestroyingTheRenderer() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val batch = readerAndroidFile("ReaderPageRasterBatchController.android.kt").readText()
		val preview = readerAssetRoot().resolve("navic-reader-page-turn-preview.js").readText()

		assertContains(controller, "rasterBatchController.cancel()")
		assertContains(batch, "cancelPageTurnPreviewBatch")
		assertContains(preview, "function cancelPageTurnPreviewBatch(")
		assertContains(preview, "this.restorePageTurnLiveComposition()")
		assertFalse(batch.contains("destroyPageTurnPreviewRenderer"))
		assertFalse(batch.contains("postDelayed"))
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
		assertContains(captureCallback, "current?.bitmap?.takeUnless { it.isRecycled }?.recycle()")
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
	fun nativeGestureIsOwnedByTheImportedPlayLikeCurlSurface() {
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val dispatch = host
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean")
			.substringBefore("private fun handleSwipeTouchEvent(")

		assertContains(dispatch, "playLikeCurlGestureOwned = usesNativePageTurnCanvas()")
		assertContains(dispatch, "if (playLikeCurlGestureOwned)")
		assertContains(dispatch, "playLikeCurlController.onPageTouchEvent(event)")
		assertContains(dispatch, "return true")
		assertFalse(dispatch.contains("pageTurnController.update("))
		assertFalse(dispatch.contains("pageTurnController.release("))
	}

	@Test
	fun nativeHostInvalidatesAnActiveTurnWhenItsLayoutSizeChanges() {
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val onSizeChanged = host
			.substringAfter("override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int)")
			.substringBefore("override fun onWindowVisibilityChanged(")

		assertContains(onSizeChanged, "if (oldw > 0 && oldh > 0 && (w != oldw || h != oldh))")
		assertContains(onSizeChanged, "pageTurnController.invalidate(\"size-changed\")")
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
	fun landscapeCurlIsHardClippedToItsActiveLeaf() {
		val renderer = readerAndroidFile("ReaderPageCurlGlRenderer.android.kt").readText()

		assertContains(renderer, "ReaderPageCurlLeafProjection.apply")
		assertContains(renderer, "clipToDisplayRect = textureSet.kind == ReaderPageTurnTransitionKind.LandscapeSpreadSlide")
		assertContains(renderer, "GLES20.glScissor")
		assertContains(renderer, "GLES20.GL_SCISSOR_TEST")
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
		val batch = readerAndroidFile("ReaderPageRasterBatchController.android.kt").readText()

		assertContains(controller, "ReaderPageTurnBundleSource")
		assertContains(controller, "pageTurnTransitionPlan")
		assertContains(controller, "beginPageTurnPreviewPreparation")
		assertContains(controller, "captureCurrentSurface")
		assertContains(controller, "hydratePreparedBundle")
		assertContains(batch, "capturePreparedRasterPage")
		assertContains(controller, "plan.targetPageIndex")
		assertContains(controller, "dispatchExactSettlement")
		assertContains(controller, "type: 'goToVisualPage'")
		assertContains(controller, "pageIndex: ${'$'}pageIndex")
		assertFalse(controller.contains("bitmapSource.capturePage(webView, direction)"))
	}

	@Test
	fun passivePrewarmDelegatesOneOrderedPreparationPlanToTheBatchController() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val prewarm = controller
			.substringAfter("fun prewarmAdjacent(): Boolean")
			.substringBefore("private fun isPrewarmActive(")
		val gesture = controller
			.substringAfter("private fun prepareBundle(")
			.substringBefore("private fun waitForPreviewReady(")

		assertContains(prewarm, "pageTurnRasterPreparationPlan")
		assertContains(prewarm, "readerPageRasterCalibrationTargets")
		assertContains(prewarm, "bundleSource.preparationMode(")
		assertContains(prewarm, "readerPageRasterFollowUpTargets")
		assertContains(prewarm, "rasterBatchController.start(")
		assertFalse(prewarm.contains("beginPageTurnPreviewPreparation"))

		val gestureCapture = gesture.indexOf("captureCurrentSurface")
		val gestureHydration = gesture.indexOf("hydratePreparedBundle")
		val gesturePreview = gesture.indexOf("beginPageTurnPreviewPreparation")
		assertTrue(gestureCapture >= 0 && gestureCapture < gestureHydration)
		assertTrue(gestureHydration >= 0 && gestureHydration < gesturePreview)
	}

	@Test
	fun passiveBatchReportsInMemoryTargetsBeforeBestEffortPersistence() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val batch = readerAndroidFile("ReaderPageRasterBatchController.android.kt").readText()

		assertContains(source, "fun ensurePersistentSnapshot(")
		assertContains(batch, "bundleSource.ensurePersistentSnapshot(")
		assertTrue(
			batch.indexOf("markCompleted(session)") < batch.indexOf("ensurePersistentSnapshot"),
			"An in-memory raster must become interactive before optional disk persistence."
		)
		assertFalse(batch.contains("postDelayed"))
		assertFalse(batch.contains("withTimeout"))
	}

	@Test
	fun passiveRasterBatchHydratesDiskBeforeSubmittingOnlyCacheMisses() {
		val batchFile = readerAndroidFile("ReaderPageRasterBatchController.android.kt")
		assertTrue(batchFile.isFile, "Native passive raster batching must have one dedicated owner.")
		val batch = batchFile.takeIf { it.isFile }?.readText().orEmpty()

		val hydrate = batch.indexOf("bundleSource.hydrateSnapshot(")
		val begin = batch.indexOf("beginPageTurnPreviewBatch")
		assertTrue(hydrate >= 0, "Persistent cache hydration must be attempted for every requested raster.")
		assertTrue(begin > hydrate, "Only cache misses may be submitted to the passive renderer batch.")
		assertContains(batch, "missingTargets")
		assertContains(batch, "ReaderPageRasterPriority.Current")
		assertContains(batch, "ReaderPageRasterPriority.NextTransition")
		assertContains(batch, "ReaderPageRasterPriority.PreviousTransition")
	}

	@Test
	fun passiveRasterBatchCapturesExactReadyItemBeforeAdvancing() {
		val batchFile = readerAndroidFile("ReaderPageRasterBatchController.android.kt")
		assertTrue(batchFile.isFile, "Native passive raster batching must have one dedicated owner.")
		val batch = batchFile.takeIf { it.isFile }?.readText().orEmpty()

		assertContains(batch, "pageTurnPreviewBatchState")
		assertContains(batch, "itemToken")
		assertContains(batch, "bundleSource.capturePreparedRasterPage(")
		assertContains(batch, "advancePageTurnPreviewBatch")
		val capture = batch.indexOf("bundleSource.capturePreparedRasterPage(")
		val advance = batch.indexOf("advancePageTurnPreviewBatch")
		assertTrue(capture >= 0 && advance > capture, "The exact ready page must be captured before native advances the batch.")
		assertContains(batch, "webView.postOnAnimation")
		assertFalse(batch.contains("postDelayed"))
		assertFalse(batch.contains("withTimeout"))
		assertFalse(batch.contains("delay("))
	}

	@Test
	fun passiveRasterBatchRejectsStaleSessionsAndReportsRealProgress() {
		val batchFile = readerAndroidFile("ReaderPageRasterBatchController.android.kt")
		assertTrue(batchFile.isFile, "Native passive raster batching must have one dedicated owner.")
		val batch = batchFile.takeIf { it.isFile }?.readText().orEmpty()

		assertContains(batch, "isSessionActive")
		assertContains(batch, "completedCount")
		assertContains(batch, "requiredCount")
		assertContains(batch, "onProgress")
		assertContains(batch, "onComplete")
		assertFalse(batch.contains("SystemClock"))
		assertFalse(batch.contains("Timer"))
	}

	@Test
	fun passiveRasterBatchCancellationAndDeferredPagesDoNotSurfaceAsFailures() {
		val batch = readerAndroidFile("ReaderPageRasterBatchController.android.kt").readText()
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val cancel = batch
			.substringAfter("fun cancel()")
			.substringBefore("private fun hydrateTarget(")
		val finish = controller
			.substringAfter("private fun finishPrewarm(")
			.substringBefore("private fun logPrewarmBoundary(")

		assertContains(cancel, "ReaderPageRasterBatchOutcome.Cancelled")
		assertFalse(cancel.contains("onComplete(false)"))
		assertContains(batch, "ReaderPageRasterBatchOutcome.Deferred")
		assertContains(finish, "ReaderPageRasterBatchOutcome.Deferred")
		assertContains(finish, "ReaderPageRasterBatchOutcome.Cancelled")
		assertContains(finish, "ReaderPagePreparationPhase.Idle")
	}

	@Test
	fun passiveRasterBatchDoesNotGateInteractionOnPersistentCacheWrites() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val batch = readerAndroidFile("ReaderPageRasterBatchController.android.kt").readText()
		val hydrate = batch
			.substringAfter("private fun hydrateTarget(")
			.substringBefore("private fun submitMissingTargets(")
		val capture = batch
			.substringAfter("private fun captureReadyItem(")
			.substringBefore("private fun advancePageTurnPreviewBatch(")
		val preparedCapture = source
			.substringAfter("fun capturePreparedRasterPage(")
			.substringBefore("private fun capturePreparedPage(")

		assertContains(hydrate, "markCompleted(session)")
		assertContains(hydrate, "bundleSource.ensurePersistentSnapshot(")
		assertTrue(
			hydrate.indexOf("markCompleted(session)") <
				hydrate.indexOf("bundleSource.ensurePersistentSnapshot("),
			"A retained in-memory raster must become interactive before optional disk persistence."
		)
		assertFalse(hydrate.contains("stage = \"snapshot-persist\""))
		assertContains(capture, "if (!captured)")
		assertFalse(capture.contains("persistent-snapshot-write-failed"))
		assertContains(preparedCapture, "onCaptured(true)")
		assertContains(preparedCapture, "schedulePersistentSnapshot(")
		assertTrue(
			preparedCapture.indexOf("onCaptured(true)") <
				preparedCapture.indexOf("schedulePersistentSnapshot("),
			"A freshly captured page must become interactive before optional disk persistence."
		)
		assertFalse(preparedCapture.contains("schedulePersistentSnapshot(cached, priority, onCaptured)"))
	}

	@Test
	fun skippedRasterPersistenceReportsItsExactReasonWithoutFailingInteraction() {
		val source = readerAndroidFile("ReaderPageTurnBundleSource.android.kt").readText()
		val persistence = source
			.substringAfter("private fun schedulePersistentSnapshot(")
			.substringBefore("private fun completeRasterPublication(")

		assertContains(persistence, "rasterPersistenceSkipped(")
		assertContains(persistence, "\"bundle-source-closed\"")
		assertContains(persistence, "\"webview-unavailable\"")
		assertContains(persistence, "\"generation-changed\"")
		assertContains(persistence, "\"descriptor-unavailable\"")
		assertContains(persistence, "\"bitmap-copy-failed\"")
		assertContains(persistence, "\"scheduler-${'$'}{result.status.name.lowercase()}\"")
		assertContains(source, "private fun rasterPersistenceSkipped(")
		assertContains(source, "Page raster persistence skipped")
		assertContains(source, "onPersisted(false)")
	}

	@Test
	fun terminalRasterFailureLogsAndDisplaysItsExactStagePageAndReason() {
		val batch = readerAndroidFile("ReaderPageRasterBatchController.android.kt").readText()
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val finish = controller
			.substringAfter("private fun finishPrewarm(")
			.substringBefore("private fun logPrewarmBoundary(")

		assertContains(batch, "ReaderPageRasterBatchOutcome.Failed(")
		assertContains(batch, "state.optString(\"message\")")
		assertContains(finish, "outcome.diagnostic")
		assertContains(finish, "error = outcome.userMessage")
		assertContains(finish, "retryable = true")
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
	fun deferredRasterPreparationWaitsForPaginationReadinessInsteadOfSpinningFrames() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val root = readerCommonUiFile("ReaderRoot.kt").readText()
		val finishPrewarm = controller
			.substringAfter("private fun finishPrewarm(outcome: ReaderPageRasterBatchOutcome)")
			.substringBefore("private fun logPrewarmBoundary")
		val readinessSetter = host
			.substringAfterLast("fun setPageTurnContentReadyKey(contentReadyKey: String?)")
			.substringBefore("fun setPageTurnVisualLocation(")

		assertFalse(
			finishPrewarm.contains("onRequestPrewarm()"),
			"A deferred passive raster must not immediately restart on the next frame"
		)
		assertContains(readinessSetter, "pageTurnController.retryPreparation()")
		assertContains(readinessSetter, "playLikeCurlController.onHostContentReady()")
		assertContains(root, "pageTurnContentReadyKey = readerPageTurnContentReadyKey(")
		assertContains(root, "controllerState.paginationProfile")
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
			.substringAfter("private fun finishPrewarm(outcome: ReaderPageRasterBatchOutcome)")
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
		assertContains(source, "restoreLiveComposition(webView, token) {")
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
		val query = controller
			.substringAfter("private fun queryRasterPreparationPlan(")
			.substringBefore("private fun expectedLayoutMode(")
		val batch = controller
			.substringAfter("private fun startRasterBatch(")
			.substringBefore("private fun obtainRasterReference(")

		assertContains(query, "obtainRasterReference(webView, session, plan.centerPageIndex, kind)")
		assertContains(batch, "reference = reference")
		assertContains(batch, "onStagingStarted = ::attachPreparationShield")
		assertFalse(batch.contains("plan.sourcePageIndex"))
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
	fun initialRasterPrewarmWaitsForTheAuthoritativeFoliateLocation() {
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val requestPrewarm = host
			.substringAfter("private fun requestPageTurnPrewarmWhenReady()")
			.substringBefore("private fun pageTurnPrewarmLayoutSignature")
		val controller = readerAndroidFile("ReaderPlayLikeCurlFoliateController.android.kt").readText()
		val refresh = controller
			.substringAfter("private fun refreshPreparedDeck()")
			.substringBefore("private fun prepareProfile(")

		assertContains(requestPrewarm, "pageTurnVisualPageIndex == null")
		assertContains(controller, "authoritativeLocationReady")
		assertContains(refresh, "!authoritativeLocationReady -> \"authoritative-location-unavailable\"")
		assertTrue(
			requestPrewarm.indexOf("pageTurnVisualPageIndex == null") <
				requestPrewarm.indexOf("pageTurnPrewarmLayoutListener != null"),
			"The host must reject provisional prewarm before it installs a frame listener."
		)
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
	fun importedExactLocationReanchorsTheLegacyPrewarmWindow() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val synchronize = controller
			.substringAfter("fun synchronizeVisualPageIndex(pageIndex: Int?, reason: String?)")
			.substringBefore("private fun begin(")
		val exactLocation = synchronize
			.substringAfter("reason == \"page-turn:exact\"")
			.substringBefore("if (slideCoordinator?.visualPageIndex == pageIndex)")

		assertContains(exactLocation, "exactCoordinator.invalidate(pageIndex)")
		assertContains(exactLocation, "cancelPrewarm()")
		assertContains(exactLocation, "onRequestPrewarm()")
		assertContains(exactLocation, "external exact page synchronized")
		assertTrue(
			exactLocation.indexOf("coordinator.settlementReported(") <
				exactLocation.indexOf("exactCoordinator.invalidate(pageIndex)"),
			"The legacy controller must complete its own matching settlement before using the imported exact fallback."
		)
	}

	@Test
	fun passiveAdjacentPrewarmMayStartDuringExactSettlement() {
		assertTrue(
			readerPageTurnCanStartPassivePrewarm(
				destroyed = false,
				sessionEnabled = true,
				visualCommitPending = false,
				idle = true
			)
		)
		assertFalse(
			readerPageTurnCanStartPassivePrewarm(
				destroyed = false,
				sessionEnabled = true,
				visualCommitPending = true,
				idle = true
			)
		)
		assertFalse(
			readerPageTurnCanStartPassivePrewarm(
				destroyed = false,
				sessionEnabled = false,
				visualCommitPending = false,
				idle = true
			)
		)

		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val prewarm = controller
			.substringAfter("fun prewarmAdjacent(): Boolean")
			.substringBefore("private fun queryRasterPreparationPlan")

		assertFalse(prewarm.contains("slideCoordinator?.activeSettlementTarget != null"))
		assertContains(prewarm, "readerPageTurnCanStartPassivePrewarm")
		assertContains(prewarm, "sessionEnabled = enabledForSession")
		assertFalse(prewarm.contains("available = isAvailable"))
	}

	@Test
	fun passivePrewarmReportsDistinctPreparationBoundariesWithoutPerFrameSpam() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()

		assertContains(controller, "logPrewarmBoundary(")
		assertContains(controller, "\"plan-unavailable\"")
		assertContains(controller, "\"layout-mismatch\"")
		assertContains(controller, "\"center-mismatch\"")
		assertContains(controller, "\"reference-unavailable\"")
		assertContains(controller, "\"batch-complete\"")
		assertContains(controller, "if (lastPrewarmBoundary == trace) return")
	}

	@Test
	fun preparedAdjacentCaptureUsesThePassiveRendererDuringExactSettlement() {
		val batch = readerAndroidFile("ReaderPageRasterBatchController.android.kt").readText()
		val preparedCapture = batch
			.substringAfter("private fun captureReadyItem(")
			.substringBefore("private fun advancePageTurnPreviewBatch(")

		assertContains(preparedCapture, "bundleSource.capturePreparedRasterPage")
		assertFalse(preparedCapture.contains("activeSettlementTarget"))
		assertFalse(preparedCapture.contains("bundleSource.captureCurrentSurface"))
	}

	@Test
	fun rotationPrewarmWaitsForNativeAndJsLayoutModesToAgree() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val bundle = readerAndroidFile("ReaderPageTurnBundle.android.kt").readText()
		val query = controller
			.substringAfter("private fun queryRasterPreparationPlan(")
			.substringBefore("private fun startRasterCalibration(")
		val begin = controller
			.substringAfter("private fun begin(deltaX: Float)")
			.substringBefore("fun prewarmAdjacent()")

		assertContains(bundle, "fun matchesLayout(spread: Boolean)")
		assertContains(query, "val expectedLayout = expectedLayoutMode(webView)")
		assertContains(query, "plan.layoutMode != expectedLayout")
		assertContains(query, "webView.postOnAnimation { queryRasterPreparationPlan(webView, session) }")
		assertContains(begin, "!plan.matchesLayout(state.spread)")
	}

	@Test
	fun passiveRasterPreparationPublishesInteractiveAndTotalProgress() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val calibration = controller
			.substringAfter("private fun startRasterCalibration(")
			.substringBefore("private fun startRasterFollowUp(")
		val batch = controller
			.substringAfter("private fun startRasterBatch(")
			.substringBefore("private fun obtainRasterReference(")

		assertContains(calibration, "rasterInteractiveRequired = calibrationTargets.size")
		assertContains(calibration, "publishPreparationState(ReaderPagePreparationPhase.Preparing)")
		assertContains(batch, "rasterInteractiveCompleted")
		assertContains(batch, "activePreparationPageNumber")
		assertContains(batch, "publishPreparationState(ReaderPagePreparationPhase.Preparing)")
		assertContains(controller, "publishPreparationState(ReaderPagePreparationPhase.Ready)")
		assertContains(controller, "ReaderPagePreparationPhase.Failed")
	}

	@Test
	fun preparationCoverIsSeparateFromNavigationOwningShellCover() {
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val preparationVisibility = host
			.substringAfter("fun setPagePreparationCoverVisible(visible: Boolean)")
			.substringBefore("fun setViewerLayerPaint(")

		assertContains(host, "pagePreparationCoverVisible")
		assertContains(host, "updateNativeCoverVisibility()")
		assertContains(preparationVisibility, "Reader native cover visibility")
		assertContains(preparationVisibility, "shell=${'$'}shellCoverVisible")
		assertContains(preparationVisibility, "preparation=${'$'}pagePreparationCoverVisible")
		assertFalse(preparationVisibility.contains("setShellCoverVisible"))
		assertFalse(preparationVisibility.contains("pageTurnController.invalidate"))
	}

	@Test
	fun preparationStateLoggingIncludesPresentationAndInteractiveReadiness() {
		val controller = readerAndroidFile("ReaderPageTurnController.android.kt").readText()
		val publish = controller
			.substringAfter("private fun publishPreparationState(")
			.substringBefore("fun updateBitmapQuality(")

		assertContains(publish, "lastPreparationStateTrace")
		assertContains(publish, "presentation=${'$'}{state.presentation}")
		assertContains(publish, "interactiveReady=${'$'}{state.interactiveReady}")
		assertContains(publish, "hasPreparedBefore=${'$'}hasPreparedBefore")
		assertContains(publish, "Page preparation state")
	}

	@Test
	fun preparationConsumesGesturesBeforeTapOrSwipeDispatch() {
		val host = readerAndroidFile("KomikkuReaderNativeFrameHost.android.kt").readText()
		val dispatch = host
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean")
			.substringBefore("private fun handleSwipeTouchEvent(")
		val preparationBlock = host
			.substringAfterLast("fun setPagePreparationGesturesBlocked(blocked: Boolean)")
			.substringBefore("fun setPagePreparationRetryKey(")

		assertContains(host, "pagePreparationGesturesBlocked")
		assertContains(dispatch, "if (pagePreparationGesturesBlocked)")
		assertContains(preparationBlock, "playLikeCurlController.cancelGesture()")
		assertFalse(
			preparationBlock.contains("pageTurnController.cancel()"),
			"Blocking user input while preparation is active must not cancel the preparation producer."
		)
		assertTrue(
			dispatch.indexOf("if (pagePreparationGesturesBlocked)") < dispatch.indexOf("handleSwipeTouchEvent(event)"),
			"Preparation must consume input before native tap or swipe dispatch."
		)
	}

	@Test
	fun readerRootOwnsAndRendersPagePreparationState() {
		val platform = readerCommonUiFile("ReaderPlatformHosts.kt").readText()
		val root = readerCommonUiFile("ReaderRoot.kt").readText()

		assertContains(platform, "onPagePreparationStateChange: (ReaderPagePreparationState) -> Unit")
		assertContains(root, "remember { mutableStateOf(ReaderPagePreparationState()) }")
		assertContains(root, "ReaderPagePreparationOverlay(")
		assertContains(root, "pagePreparationCoverVisible =")
		assertContains(root, "pagePreparationGesturesBlocked =")
	}
}
