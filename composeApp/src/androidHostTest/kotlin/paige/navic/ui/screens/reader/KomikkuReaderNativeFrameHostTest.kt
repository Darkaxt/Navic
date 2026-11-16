package paige.navic.ui.screens.reader

import java.io.File
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPageNewPointerDecision
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationPresentation
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPageRendererReadinessState
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.readerPagePreparationState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KomikkuReaderNativeFrameHostTest {
	private val hostFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"KomikkuReaderNativeFrameHost.android.kt"
	)

	@Test
	fun startupShellHandoffIsOneShotAcrossAnOrdinaryReturnedCover() {
		val gate = ReaderStartupShellHandoffGate()
		assertTrue(gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true))
		val attempt = assertNotNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
		var prepared = 0
		var rejected = 0

		gate.completeAttempt(
			attempt = attempt,
			shellVisible = true,
			canvasEnabled = true,
			rasterPhase = ReaderPagePreparationPhase.Ready,
			textureDeck = ReaderTextureDeckState.Ready,
			onPrepared = { prepared += 1 },
			onRejected = { rejected += 1 }
		)

		assertEquals(1, prepared)
		assertEquals(0, rejected)
		assertTrue(gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true))
		assertTrue(gate.consumePreparedHandoff())
		assertFalse(gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true))
		assertNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)

		gate.resetForNewViewer()
		assertTrue(gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true))
	}

	@Test
	fun nonCanvasStartupDismissalRetiresLaterCanvasHandoff() {
		val gate = ReaderStartupShellHandoffGate()

		assertFalse(
			gate.consumesCanvasShellPageAction(
				shellVisible = true,
				canvasEnabled = false
			)
		)
		assertFalse(gate.consumePreparedHandoff())
		assertFalse(
			gate.consumesCanvasShellPageAction(
				shellVisible = true,
				canvasEnabled = true
			)
		)
		assertNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
	}

	@Test
	fun regressedReadinessRejectsCommittedStartupShieldAndPermitsRetry() {
		val gate = ReaderStartupShellHandoffGate()
		val attempt = assertNotNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
		var prepared = 0
		var staleShieldDismissed = 0

		gate.completeAttempt(
			attempt = attempt,
			shellVisible = true,
			canvasEnabled = true,
			rasterPhase = ReaderPagePreparationPhase.Preparing,
			textureDeck = ReaderTextureDeckState.Preparing,
			onPrepared = { prepared += 1 },
			onRejected = { staleShieldDismissed += 1 }
		)

		assertEquals(0, prepared)
		assertEquals(1, staleShieldDismissed)
		assertFalse(gate.attemptInFlight)
		assertNotNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
	}

	@Test
	fun readableLayoutRegenerationKeepsTheLivePageVisibleButBlocksTurns() {
		// #50: after a rotation both the raster and the renderer side report blocking states.
		val rasterState = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 12,
			completedCount = 2,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)
		val blocked = readerMergedPagePreparationState(
			pageTurnCanvasEnabled = true,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState(
				interaction = ReaderPageInteractionState.BlockingProfileRegeneration
			)
		)
		assertEquals(ReaderPagePreparationPresentation.Cover, blocked.presentation)

		val regenerating = readerLayoutRegenerationPreparationState(blocked, regeneratingReadableLayout = true)
		assertEquals(ReaderPagePreparationPresentation.Compact, regenerating.presentation)
		assertTrue(regenerating.showsProgress)
		assertFalse(regenerating.interactiveReady)
		assertEquals(blocked.operationPolicy, regenerating.operationPolicy)
		assertFalse(
			readerNativePresentationLayerVisibility(
				shellCoverVisible = false,
				pagePreparationCoverVisible =
					regenerating.presentation == ReaderPagePreparationPresentation.Cover,
				hasValidatedRasterPresentation = false
			).preparationShield
		)

		// While the new layout is still being measured, the live page shows without any card.
		val measuring = readerLayoutRegenerationPreparationState(
			readerMergedPagePreparationState(
				pageTurnCanvasEnabled = true,
				pageTurnContentReady = false,
				rasterState = rasterState,
				rendererState = ReaderPageRendererReadinessState()
			),
			regeneratingReadableLayout = true
		)
		assertEquals(ReaderPagePreparationPresentation.Compact, measuring.presentation)
		assertFalse(measuring.showsProgress)
		assertFalse(measuring.interactiveReady)

		// Initial open (no readable layout) and failures stay fail-closed.
		assertEquals(blocked, readerLayoutRegenerationPreparationState(blocked, regeneratingReadableLayout = false))
		val failed = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Failed,
			requiredCount = 12,
			completedCount = 2,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(interaction = ReaderPageInteractionState.Failed)
		)
		assertEquals(failed, readerLayoutRegenerationPreparationState(failed, regeneratingReadableLayout = true))
	}

	@Test
	fun readableLayoutRegenerationIsOnlyStartedByLayoutChanges() {
		val source = File("src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt").readText()
			.substringAfter("private class KomikkuReaderNativeViewerContainer(")
		val sizeChanged = source.substringAfter("override fun onSizeChanged(").substringBefore("override fun onWindowVisibilityChanged(")
		assertTrue(sizeChanged.indexOf("beginReadableLayoutRegeneration()") in 0 until sizeChanged.indexOf("onHostSizeChanged()"))
		for (entry in listOf("fun setPageTurnReadingDirection(", "fun setPageTurnBitmapQuality(", "fun setPageTurnSnapshotKey(")) {
			assertContains(source.substringAfter(entry).substringBefore("\n\t}"), "beginReadableLayoutRegeneration()", message = entry)
		}
		val begin = source.substringAfter("private fun beginReadableLayoutRegeneration()").substringBefore("\n\t}")
		assertContains(begin, "if (shellCoverVisible) return")
		assertContains(begin, "lastPublishedPagePreparationPresentation == ReaderPagePreparationPresentation.Hidden")
		assertContains(begin, "readableBeforeSettingsMutation")
		// A settings mutation captures readability when it starts, before its relocations publish
		// the cover, and keeps the flag (and prewarm gate) until it commits or is cancelled.
		val started = source.substringAfter("private fun onSettingsMutationStarted()").substringBefore("\n\t}")
		assertContains(started, "settingsMutationInFlight = true")
		assertContains(started, "if (readableBeforeSettingsMutation) regeneratingReadableLayout = true")
		assertContains(started, "removePageTurnPrewarmLayoutListener()")
		val finished = source.substringAfter("private fun onSettingsMutationFinished()").substringBefore("\n\t}")
		assertContains(finished, "settingsMutationInFlight = false")
		assertContains(finished, "requestPageTurnPrewarmWhenReady()")
		assertContains(source, "merged.presentation == ReaderPagePreparationPresentation.Hidden && !settingsMutationInFlight")
		val prewarm = source.substringAfter("private fun requestPageTurnPrewarmWhenReady()").substringBefore("task4ResourceTeardownStarted ||")
		assertContains(prewarm, "if (settingsMutationInFlight) return")
		for (relayout in listOf("\"settings-changed\"", "\"reading-direction\"", "\"size-changed\"")) {
			assertContains(source.substringAfter("pageRasterPreparationController.invalidate(\n\t\t\treason = $relayout").take(80),"layoutRegeneration = regeneratingReadableLayout", message = relayout)
		}
		for (exit in listOf(
			"playLikeCurlController.invalidate(\"viewer-replaced\")",
			"playLikeCurlController.invalidate(\"shell-cover-visible\")",
			"playLikeCurlController.onHostWindowHidden()"
		)) {
			val before = source.substringBefore(exit).takeLast(160)
			assertContains(before, "regeneratingReadableLayout = false", message = exit)
		}
	}

	@Test
	fun disabledCanvasStaysBlockedUntilPublicationContentBecomesReady() {
		val rasterState = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Idle,
			requiredCount = 0,
			completedCount = 0,
			interactiveRequiredCount = 0,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		val pending = readerMergedPagePreparationState(
			pageTurnCanvasEnabled = false,
			pageTurnContentReady = false,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState()
		)
		val ready = readerMergedPagePreparationState(
			pageTurnCanvasEnabled = false,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState()
		)

		assertEquals(ReaderPagePreparationPhase.Idle, pending.phase)
		assertEquals(ReaderPagePreparationPresentation.Cover, pending.presentation)
		assertFalse(pending.interactiveReady)
		assertTrue(pending.showsProgress)
		assertEquals(ReaderPagePreparationPhase.Ready, ready.phase)
		assertEquals(ReaderPagePreparationPresentation.Hidden, ready.presentation)
		assertTrue(ready.interactiveReady)
	}

	@Test
	fun disabledCanvasCannotPublishAStuckPreparationCover() {
		val rasterState = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Idle,
			requiredCount = 0,
			completedCount = 0,
			interactiveRequiredCount = 0,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		val merged = readerMergedPagePreparationState(
			pageTurnCanvasEnabled = false,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState()
		)

		assertEquals(ReaderPagePreparationPhase.Ready, merged.phase)
		assertEquals(ReaderPagePreparationPresentation.Hidden, merged.presentation)
		assertTrue(merged.operationPolicy.newPointer is ReaderPageNewPointerDecision.Accept)
	}

	@Test
	fun enabledCanvasStillPublishesBlockingRasterPreparation() {
		val rasterState = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 3,
			completedCount = 1,
			interactiveRequiredCount = 2,
			interactiveCompletedCount = 1,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		val merged = readerMergedPagePreparationState(
			pageTurnCanvasEnabled = true,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState(
				textureDeck = ReaderTextureDeckState.Preparing,
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		assertEquals(ReaderPagePreparationPhase.Preparing, merged.phase)
		assertEquals(ReaderPagePreparationPresentation.Cover, merged.presentation)
		assertEquals(1, merged.completedCount)
	}
	@Test
	fun canvasAndContentReadinessTransitionsRepublishTheInputGate() {
		val source = hostFile.readText()
		val canvasTransition = source
			.substringAfterLast("fun setPageTurnCanvasEnabled(enabled: Boolean)")
			.substringBefore("fun setPageTurnReadingDirection(")
		val contentTransition = source
			.substringAfterLast("fun setPageTurnContentReadyKey(contentReadyKey: String?)")
			.substringBefore("fun setPageTurnPaginationStatus(")

		assertContains(canvasTransition, "publishMergedPagePreparationState()")
		assertContains(contentTransition, "publishMergedPagePreparationState()")
		assertTrue(
			contentTransition.indexOf("publishMergedPagePreparationState()") <
				contentTransition.indexOf("if (contentReadyKey == null) return")
		)
	}

	@Test
	fun busyFeedbackUsesTheOneTerminalPublisherWithoutInterceptingPreparationGestures() {
		val source = hostFile.readText()

		assertContains(source, "readerPageGestureShouldShowBusyFeedback(outcome)")
		assertContains(source, "onRendererBusyGestureRejected()")
		assertFalse(source.contains("composeOverlay.isClickable = visible"))
	}

	@Test
	fun busyFeedbackClearsOnlyAfterActualRendererPointerAdmission() {
		val source = hostFile.readText()

		assertContains(
			source,
			"fun canAcceptNewPointer(): Boolean = playLikeCurlController.isAvailable"
		)
		assertContains(source, "currentNativeFrameRoot?.canAcceptNewPointer() == true")
		assertFalse(
			source.contains(
				"pageOperationPolicy.newPointer == ReaderPageNewPointerDecision.Accept"
			)
		)
	}

	@Test
	fun busyFeedbackUsesANonTouchableWindowAboveTheOnTopCurlSurface() {
		val hostSource = hostFile.readText()
		val popupSource = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderRendererBusyPopup.android.kt"
		).readText()

		assertContains(hostSource, "ReaderRendererBusyPopup(")
		assertContains(popupSource, "PopupWindow(")
		assertContains(popupSource, "isFocusable = false")
		assertContains(popupSource, "isTouchable = false")
		assertContains(
			popupSource,
			"windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL"
		)
		assertFalse(hostSource.contains("androidx.compose.ui.window.Popup"))
	}

	@Test
	fun retainedValidatedPresentationSurvivesOnlyTransientRendererReadiness() {
		val ownership = ReaderRetainedValidatedPresentationOwnership()

		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Preparing)
		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Ready)
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = false))

		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Preparing)
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = false))
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Settling)
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = false))

		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Empty)
		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = true))

		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Ready)
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Failed)
		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
	}

	@Test
	fun busyFeedbackMinimumTimerIsOwnedByFullyVisibleNestedOverlay() {
		val source = hostFile.readText()

		assertContains(
			source,
			"val rendererBusyFeedbackVisibility = remember { MutableTransitionState(false) }"
		)
		assertContains(source, "fullyVisibleRejectionToken = activeToken")
		assertContains(
			source,
			"readerRendererBusyFeedbackCanStartMinimumTimer("
		)
		assertFalse(source.contains("var rendererBusyFeedbackVisible by remember"))
	}

	@Test
	fun publicationShellArtworkIsVisibleOnlyForControllerOwnedShellCover() {
		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = true,
				preparationShield = false
			),
			readerNativePresentationLayerVisibility(
				shellCoverVisible = true,
				pagePreparationCoverVisible = true,
				hasValidatedRasterPresentation = false
			)
		)
	}

	@Test
	fun preparationRetainsValidatedRasterWithoutPublicationCoverOrNeutralShield() {
		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = false
			),
			readerNativePresentationLayerVisibility(
				shellCoverVisible = false,
				pagePreparationCoverVisible = true,
				hasValidatedRasterPresentation = true
			)
		)
	}

	@Test
	fun preparationWithoutValidatedRasterUsesNeutralFailClosedShield() {
		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = true
			),
			readerNativePresentationLayerVisibility(
				shellCoverVisible = false,
				pagePreparationCoverVisible = true,
				hasValidatedRasterPresentation = false
			)
		)
	}

	@Test
	fun reattachedTornDownHostIsRebuiltInsteadOfStayingInert() {
		val hostSource = hostFile.readText()
		val composable = hostSource
			.substringAfter("actual fun KomikkuReaderNativeFrameHost(")
			.substringBefore("private class ReaderNestedComposeDisposalQueue")
		val root = hostSource
			.substringAfter("private class KomikkuReaderNativeFrameRoot")
			.substringBefore("private class KomikkuReaderNativeShellCoverView")
		val rootAttach = root
			.substringAfter("override fun onAttachedToWindow()")
			.substringBefore("\n\t}")
		val viewer = hostSource
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private fun View.findDescendantWebView")

		// Rotation can move the navigation entry and reattach the same View after
		// window detach already destroyed its controllers irreversibly.
		assertContains(composable, "var nativeHostGeneration by remember { mutableIntStateOf(0) }")
		assertContains(composable, "key(nativeHostGeneration) {\n\t\tAndroidView(")
		assertContains(composable, "setOnTornDownHostReattached {")
		assertContains(composable, "nativeHostGeneration += 1")
		assertTrue(
			composable.indexOf("key(nativeHostGeneration)") <
				composable.indexOf("onRelease = { root ->"),
			"The keyed generation must own the AndroidView release path."
		)
		assertContains(rootAttach, "super.onAttachedToWindow()")
		assertContains(rootAttach, "if (viewerContainer.hostResourcesTornDown)")
		assertContains(rootAttach, "post { onTornDownHostReattached?.invoke() }")
		assertContains(viewer, "val hostResourcesTornDown: Boolean")
		assertContains(viewer, "get() = task4ResourceTeardownStarted")
		assertContains(
			viewer.substringAfter("private fun teardownTask4Resources()"),
			"task4ResourceTeardownStarted = true"
		)

		// The rebuilt host receives readiness before its visual page; learning the page
		// must re-request prewarm or preparation never starts.
		val visualLocation = viewer
			.substringAfter("fun setPageTurnVisualLocation(")
			.substringBefore("fun setShellCoverVisible(")
		assertContains(
			visualLocation,
			"val visualLocationBecameKnown = pageTurnVisualPageIndex == null && normalized != null"
		)
		assertContains(visualLocation, "if (visualLocationBecameKnown) requestPageTurnPrewarmWhenReady()")
		assertContains(
			viewer.substringAfter("private fun requestPageTurnPrewarmWhenReady()"),
			"pageTurnVisualPageIndex == null ||"
		)
	}
}
