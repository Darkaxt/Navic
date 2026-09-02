package paige.navic.ui.screens.reader

import android.view.MotionEvent
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderLegacyLiveCompatibilityContext
import paige.navic.reader.ReaderLegacyLiveCompatibilityIdentity
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPagePreparationFacts
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPageRendererReadinessState
import paige.navic.reader.ReaderPreparationPresentation
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderDiagnosticPresentation
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationInputPolicy
import paige.navic.reader.ReaderPresentationLayer
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderPublicationIdentity
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.readerPresentationDecision
import paige.navic.reader.readerPresentationReduce
import paige.navic.reader.readerPagePreparationState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class KomikkuReaderNativeFrameHostTest {
	private val hostFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"KomikkuReaderNativeFrameHost.android.kt"
	)

	@Test
	fun nativeHostApplicationKeepsLayerPreparationDiagnosticAndInputOnOneDecision() {
		val binding = ReaderPresentationBinding(
			foliateSessionId = "application-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				foliateSessionId = "application-session",
				commitSequence = 4L
			),
			rasterGeneration = 5L,
			textureGeneration = 6L,
			preparationGeneration = 7L
		)
		val coverProof = ReaderShellCoverCommitProof(
			token = ReaderPresentationToken(8L),
			binding = binding,
			coverGeneration = 9L,
			presentedFrame = 10L,
			viewportWidth = 1200,
			viewportHeight = 800
		)
		val decision = readerPresentationReduce(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.ShellCover(coverProof),
				binding = binding,
				preparationFacts = ReaderPagePreparationFacts(
					phase = ReaderPagePreparationPhase.Preparing,
					generation = 7L,
					completedCount = 1,
					requiredCount = 4,
					readiness = ReaderPageReadinessState(
						textureDeck = ReaderTextureDeckState.Preparing,
						interaction = ReaderPageInteractionState.BlockingInitialPreparation
					)
				),
				nextTokenValue = 11L
			),
			ReaderPresentationEvent.ShellCoverDismissalRequested
		).decision

		val application = readerNativePresentationApplication(decision)

		assertSame(decision, application.decision)
		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = true,
				preparationShield = false
			),
			application.layers
		)
		assertEquals(decision.preparationPresentation, application.preparation)
		assertEquals(decision.diagnosticPresentation, application.diagnostic)
		assertEquals(decision.inputPolicy, application.inputPolicy)
		assertIs<ReaderPreparationPresentation.Blocking>(application.preparation)
		assertEquals(ReaderDiagnosticPresentation.Hidden, application.diagnostic)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, application.inputPolicy)
	}

	@Test
	fun unavailableRawCoverIntentCannotSelectOrExposeAShellCover() {
		val unavailableDecision = readerPresentationDecision(ReaderPresentationState())
		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = false
			),
			readerNativePresentationApplication(unavailableDecision).layers
		)

		val binding = ReaderPresentationBinding(
			foliateSessionId = "native-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = null,
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding = binding,
			transitionToken = null,
			presentedFrame = 7L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 4L,
			textureGeneration = 5L
		)
		val nativeDecision = readerPresentationDecision(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.SettledNativePage(
					ReaderPresentationFrameOwner.NativePage(nativeProof)
				),
				binding = binding,
				preparationFacts = ReaderPagePreparationFacts(
					phase = ReaderPagePreparationPhase.Ready,
					generation = 6L,
					readiness = ReaderPageReadinessState(
						textureDeck = ReaderTextureDeckState.Ready,
						interaction = ReaderPageInteractionState.Ready
					)
				)
			)
		)

		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = false
			),
			readerNativePresentationApplication(nativeDecision).layers
		)
	}

	@Test
	fun chromeOnlyUsesAChromeGestureRouteThatNeverDispatchesToPageCurlOrContent() {
		val source = hostFile.readText()
		val modeSelector = source
			.substringAfter("internal fun readerPagePhysicalDispatchMode(")
			.substringBefore("internal interface ReaderPagePhysicalDispatchTarget")
		val physicalDispatch = source
			.substringAfter("internal fun readerDispatchPagePhysicalEvent(")
			.substringBefore("internal data class ReaderLegacyLivePointerContext")
		val chromeTarget = source
			.substringAfter("override fun dispatchChromeOnly(event: MotionEvent): Boolean =")
			.substringBefore("override fun dispatchDenied(event: MotionEvent)")
		val shellCoverTarget = source
			.substringAfter("override fun dispatchShellCover(event: MotionEvent): Boolean =")
			.substringBefore("override fun dispatchLiveEngine(event: MotionEvent)")
		val shellCoverDispatch = source
			.substringAfter("private fun dispatchShellCoverPointerEvent(event: MotionEvent): Boolean {")
			.substringBefore("private fun dispatchLegacyReaderPointerEvent")

		assertContains(modeSelector, "ReaderPresentationInputPolicy.ChromeOnly ->")
		assertContains(modeSelector, "ReaderPagePhysicalDispatchMode.ChromeOnly")
		assertContains(
			physicalDispatch,
			"ReaderPagePhysicalDispatchMode.ChromeOnly -> target.dispatchChromeOnly(event)"
		)
		assertContains(chromeTarget, "dispatchChromeOnlyPointerEvent(event)")
		assertFalse(chromeTarget.contains("dispatchPlayLikeCurlPointerEvent"))
		assertFalse(chromeTarget.contains("dispatchLegacyReaderPointerEvent"))
		assertFalse(chromeTarget.contains("viewerContentContainer.dispatchTouchEvent"))
		assertContains(shellCoverTarget, "dispatchShellCoverPointerEvent(event)")
		assertContains(shellCoverDispatch, "shellCoverView?.dispatchTouchEvent(event)")
		assertFalse(shellCoverDispatch.contains("viewerContentContainer.dispatchTouchEvent"))
		assertFalse(shellCoverDispatch.contains("super.dispatchTouchEvent"))
	}

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
	fun hostPreparationFactsStayBlockedUntilPublicationContentBecomesReady() {
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

		val pending = readerHostPagePreparationState(
			pageTurnCanvasEnabled = false,
			pageTurnContentReady = false,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState()
		)
		val ready = readerHostPagePreparationState(
			pageTurnCanvasEnabled = false,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState()
		)

		assertEquals(ReaderPagePreparationPhase.Idle, pending.phase)
		assertEquals(
			ReaderPageInteractionState.BlockingInitialPreparation,
			pending.readiness.interaction
		)
		assertEquals(ReaderPagePreparationPhase.Ready, ready.phase)
		assertEquals(ReaderTextureDeckState.Ready, ready.readiness.textureDeck)
		assertEquals(ReaderPageInteractionState.Ready, ready.readiness.interaction)
	}

	@Test
	fun enabledCanvasMergesRendererReadinessIntoRawPreparationFacts() {
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

		val merged = readerHostPagePreparationState(
			pageTurnCanvasEnabled = true,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState(
				textureDeck = ReaderTextureDeckState.Preparing,
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		assertEquals(ReaderPagePreparationPhase.Preparing, merged.phase)
		assertEquals(ReaderTextureDeckState.Preparing, merged.readiness.textureDeck)
		assertEquals(ReaderPageInteractionState.BlockingInitialPreparation, merged.readiness.interaction)
		assertEquals(1, merged.completedCount)
	}

	@Test
	fun canvasAndContentReadinessTransitionsRepublishPreparationFacts() {
		val source = hostFile.readText()
		val canvasTransition = source
			.substringAfterLast("fun setPageTurnCanvasEnabled(enabled: Boolean)")
			.substringBefore("fun setPageTurnReadingDirection(")
		val contentTransition = source
			.substringAfterLast("fun setPageTurnContentReadyKey(contentReadyKey: String?)")
			.substringBefore("fun setPageTurnPaginationStatus(")

		assertContains(canvasTransition, "publishPagePreparationFacts()")
		assertContains(contentTransition, "publishPagePreparationFacts()")
		assertTrue(
			contentTransition.indexOf("publishPagePreparationFacts()") <
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
	fun neutralBlockingDecisionUsesOnlyTheFailClosedPreparationShield() {
		val decision = readerPresentationDecision(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.BlockingPreparation(
					retainedFrame = ReaderPresentationFrameOwner.Neutral
				),
				preparationFacts = ReaderPagePreparationFacts(
					phase = ReaderPagePreparationPhase.Preparing,
					generation = 1L,
					completedCount = 0,
					requiredCount = 0,
					readiness = ReaderPageReadinessState(
						interaction = ReaderPageInteractionState.BlockingInitialPreparation
					)
				)
			)
		)

		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = true
			),
			readerNativePresentationApplication(decision).layers
		)
	}

	@Test
	fun productionBindingReporterConfirmsReducerStateAndUsesAtomicCombinedReplacement() {
		val session = "fixture-session"
		val bindingA = ReaderPresentationBinding(
			foliateSessionId = session,
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(session, 1L),
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)
		val combinedB = bindingA.copy(
			viewportGeneration = 7L,
			profileGeneration = 8L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(session, 2L),
			rasterGeneration = 9L,
			textureGeneration = 10L,
			preparationGeneration = 11L
		)
		val pureDestinationC = combinedB.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(session, 3L),
			rasterGeneration = 12L,
			textureGeneration = 13L,
			preparationGeneration = 14L
		)
		val reporter = ReaderPresentationBindingReporter()
		var state = ReaderPresentationState()

		val opened = assertNotNull(
			reporter.update(
				confirmedTargetBinding = readerPresentationDecision(state).targetBinding,
				currentBinding = bindingA,
				publicationOpenPending = true,
				relocationPending = false
			)
		)
		assertTrue(opened is ReaderPresentationEvent.PublicationOpened)
		assertNull(reporter.lastReportedBinding)
		state = readerPresentationReduce(state, opened).state
		assertNull(
			reporter.update(
				confirmedTargetBinding = readerPresentationDecision(state).targetBinding,
				currentBinding = bindingA,
				publicationOpenPending = false,
				relocationPending = false
			)
		)
		assertEquals(bindingA, reporter.lastReportedBinding)

		val proofA = ReaderNativePagePresentationProof(
			binding = bindingA,
			transitionToken = null,
			presentedFrame = 1L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 4L,
			textureGeneration = 5L
		)
		state = readerPresentationReduce(
			state,
			ReaderPresentationEvent.NativePagePresented(proofA)
		).state
		val combinedEvent = assertIs<ReaderPresentationEvent.BindingReplaced>(
			reporter.update(
				confirmedTargetBinding = readerPresentationDecision(state).targetBinding,
				currentBinding = combinedB,
				publicationOpenPending = false,
				relocationPending = true
			)
		)
		assertEquals(bindingA, combinedEvent.previousBinding)
		assertEquals(combinedB, combinedEvent.binding)
		assertEquals(bindingA, reporter.lastReportedBinding)

		assertNull(
			reporter.update(
				confirmedTargetBinding = bindingA,
				currentBinding = combinedB,
				publicationOpenPending = false,
				relocationPending = false
			)
		)
		assertEquals(bindingA, reporter.lastReportedBinding)

		val replacement = readerPresentationReduce(state, combinedEvent)
		state = replacement.state
		assertEquals(ReaderPresentationAuthority.Unavailable, state.authority)
		assertEquals(
			listOf(
				paige.navic.reader.ReaderPresentationEffect.ReleaseStalePresentation(
					null,
					bindingA
				)
			),
			replacement.effects
		)
		assertNull(
			reporter.update(
				confirmedTargetBinding = readerPresentationDecision(state).targetBinding,
				currentBinding = combinedB,
				publicationOpenPending = false,
				relocationPending = false
			)
		)
		assertEquals(combinedB, reporter.lastReportedBinding)

		assertNull(
			reporter.update(
				confirmedTargetBinding = combinedB,
				currentBinding = combinedB,
				publicationOpenPending = false,
				relocationPending = false
			)
		)
		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(
				confirmedTargetBinding = combinedB,
				currentBinding = pureDestinationC,
				publicationOpenPending = false,
				relocationPending = true
			)
		)
		assertEquals(pureDestinationC, relocation.binding)
	}

	@Test
	fun bindingReporterPublishesPartialOpenThenTypedExactCompletion() {
		val partial = ReaderPresentationBinding(
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture-session", 1L),
			preparationGeneration = 6L
		)
		val complete = partial.copy(rasterGeneration = 7L, textureGeneration = 8L)
		val reporter = ReaderPresentationBindingReporter()
		var state = ReaderPresentationState()

		val opened = assertIs<ReaderPresentationEvent.PublicationOpened>(
			reporter.update(
				confirmedTargetBinding = null,
				currentBinding = partial,
				publicationOpenPending = true,
				relocationPending = false
			)
		)
		state = readerPresentationReduce(state, opened).state
		assertNull(
			reporter.update(
				confirmedTargetBinding = state.binding,
				currentBinding = partial,
				publicationOpenPending = false,
				relocationPending = false
			)
		)
		assertEquals(partial, reporter.lastReportedBinding)

		val completion = assertIs<ReaderPresentationEvent.BindingCompleted>(
			reporter.update(
				confirmedTargetBinding = state.binding,
				currentBinding = complete,
				publicationOpenPending = false,
				relocationPending = false
			)
		)
		assertEquals(partial, completion.previousBinding)
		assertEquals(complete, completion.binding)
		assertEquals(partial, reporter.lastReportedBinding)
		state = readerPresentationReduce(state, completion).state
		assertNull(
			reporter.update(
				confirmedTargetBinding = state.binding,
				currentBinding = complete,
				publicationOpenPending = false,
				relocationPending = false
			)
		)
		assertEquals(complete, reporter.lastReportedBinding)
	}

	@Test
	fun coldCanvasBindingIsPartialUntilAnExactPreparedDeckArrives() {
		val destination = ReaderDestinationCommitIdentity("fixture-session", 4L)
		val snapshot = ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = true,
			windowVisible = true,
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(3L),
			destinationCommitIdentity = destination,
			preparationGeneration = 6L,
			visualPageIndex = 7,
			preparedDeck = null,
			preparedDeckAdmitted = false
		)
		val partial = assertNotNull(readerPresentationHostBinding(snapshot))

		assertEquals("fixture-session", partial.foliateSessionId)
		assertEquals(3L, partial.profileGeneration)
		assertEquals(destination, partial.destinationCommitIdentity)
		assertEquals(6L, partial.preparationGeneration)
		assertNull(partial.rasterGeneration)
		assertNull(partial.textureGeneration)

		val exactDeck = ReaderPagePreparedActiveDeck(
			rasterProfileEpoch = 3L,
			rasterEpoch = 8L,
			sourceCenterPageIndex = 7,
			generationId = 9L,
			preparationGeneration = 6L
		)
		assertEquals(
			partial.copy(rasterGeneration = 8L, textureGeneration = 9L),
			readerPresentationHostBinding(
				snapshot.copy(preparedDeck = exactDeck, preparedDeckAdmitted = true)
			)
		)
	}

	@Test
	fun staleLateDeckNeverCompletesOrFabricatesRendererIdentity() {
		val base = ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = true,
			windowVisible = true,
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(3L),
			destinationCommitIdentity = null,
			preparationGeneration = 6L,
			visualPageIndex = 7,
			preparedDeck = null,
			preparedDeckAdmitted = true
		)
		val staleDecks = listOf(
			ReaderPagePreparedActiveDeck(4L, 8L, 7, 9L, 6L),
			ReaderPagePreparedActiveDeck(3L, 8L, 8, 9L, 6L),
			ReaderPagePreparedActiveDeck(3L, 8L, 7, 9L, 5L)
		)

		staleDecks.forEach { staleDeck ->
			val binding = assertNotNull(
				readerPresentationHostBinding(base.copy(preparedDeck = staleDeck))
			)
			assertNull(binding.rasterGeneration)
			assertNull(binding.textureGeneration)
		}
		val unadmitted = assertNotNull(
			readerPresentationHostBinding(
				base.copy(
					preparedDeck = ReaderPagePreparedActiveDeck(3L, 8L, 7, 9L, 6L),
					preparedDeckAdmitted = false
				)
			)
		)
		assertNull(unadmitted.rasterGeneration)
		assertNull(unadmitted.textureGeneration)
	}

	@Test
	fun provisionalProfileBootstrapIsReportableWhileUnknownFactsStayFenced() {
		val completeFacts = ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = true,
			windowVisible = true,
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(3L),
			destinationCommitIdentity = null,
			preparationGeneration = 6L,
			visualPageIndex = 7,
			preparedDeck = null,
			preparedDeckAdmitted = false
		)

		assertNull(readerPresentationHostBinding(completeFacts.copy(foliateSessionId = null)))
		assertNull(readerPresentationHostBinding(completeFacts.copy(viewportWidth = 0)))
		val provisional = assertNotNull(
			readerPresentationHostBinding(
				completeFacts.copy(
					profileIdentity = ReaderPresentationHostProfileIdentity.Provisional
				)
			)
		)
		assertEquals(0L, provisional.profileGeneration)
		assertNull(provisional.rasterGeneration)
		assertNull(provisional.textureGeneration)
		assertNotNull(readerPresentationHostBinding(completeFacts))
	}

	@Test
	fun hostUsesTypedProfileIdentityAndRetriesBootstrapAfterFailure() {
		val bindingSource = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPresentationHostBinding.android.kt"
		).readText()
		val retry = hostFile.readText()
			.substringAfter("fun retryPreparation(effect: ReaderPresentationEffect.RetryPreparation)")
			.substringBefore("private fun onPreparedActiveDeckChanged")

		assertContains(bindingSource, "sealed interface ReaderPresentationHostProfileIdentity")
		assertContains(bindingSource, "data object Provisional")
		assertContains(bindingSource, "data class Resolved")
		assertFalse(bindingSource.contains("val profileGeneration: Long?"))
		assertContains(retry, "if (rasterProfileEpoch == null)")
		assertContains(retry, "requestPageTurnPrewarmWhenReady()")
	}

	@Test
	fun nonCanvasModeNeverBypassesPresentationPolicyToDirectWebView() {
		val snapshot = ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = false,
			windowVisible = true,
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(3L),
			destinationCommitIdentity = null,
			preparationGeneration = 6L,
			visualPageIndex = 7,
			preparedDeck = null,
			preparedDeckAdmitted = false
		)

		assertNull(readerPresentationHostBinding(snapshot))
		val cold = coldLegacyLiveCompatibilityContext()
		assertEquals(
			ReaderPagePhysicalDispatchMode.LegacyLive,
			readerPagePhysicalDispatchMode(
				pageTurnCanvasEnabled = false,
				presentationInputPolicy = ReaderPresentationInputPolicy.RecoveryOnly,
				legacyLiveCompatibilityContext = cold
			)
		)
		assertEquals(
			ReaderPagePhysicalDispatchMode.Denied,
			readerPagePhysicalDispatchMode(
				pageTurnCanvasEnabled = false,
				presentationInputPolicy = ReaderPresentationInputPolicy.RecoveryOnly
			)
		)
		val expectedByPolicy = listOf(
			ReaderPresentationInputPolicy.ChromeOnly to ReaderPagePhysicalDispatchMode.ChromeOnly,
			ReaderPresentationInputPolicy.ShellCover to ReaderPagePhysicalDispatchMode.ShellCover,
			ReaderPresentationInputPolicy.ClaimedCurl(ReaderPresentationToken(1L)) to
				ReaderPagePhysicalDispatchMode.PlayLikeCurl,
			ReaderPresentationInputPolicy.NativePage(
				paige.navic.reader.readerPageOperationPolicy(ReaderPageReadinessState())
			) to ReaderPagePhysicalDispatchMode.Denied,
			ReaderPresentationInputPolicy.LiveEngine to ReaderPagePhysicalDispatchMode.Denied
		)
		expectedByPolicy.forEach { (policy, expected) ->
			assertEquals(
				expected,
				readerPagePhysicalDispatchMode(
					pageTurnCanvasEnabled = false,
					presentationInputPolicy = policy
				),
				"policy=$policy"
			)
		}
		listOf(
			ReaderPresentationLifecycleState.Background,
			ReaderPresentationLifecycleState.Destroyed
		).forEach { lifecycle ->
			assertEquals(
				ReaderPagePhysicalDispatchMode.Denied,
				readerPagePhysicalDispatchMode(
					pageTurnCanvasEnabled = false,
					presentationInputPolicy = ReaderPresentationInputPolicy.ChromeOnly,
					legacyLiveCompatibilityContext =
						ReaderLegacyLiveCompatibilityContext.Denied(lifecycle)
				)
			)
		}
	}

	@Test
	fun physicalDispatchInvokesOnlyTheSelectedTypedTarget() {
		val calls = mutableListOf<String>()
		val target = object : ReaderPagePhysicalDispatchTarget {
			override fun dispatchCueMap(event: MotionEvent) = calls.add("cue-map")
			override fun dispatchChromeOnly(event: MotionEvent) = calls.add("chrome-only")
			override fun dispatchDenied(event: MotionEvent) = calls.add("denied")
			override fun dispatchLegacy(event: MotionEvent) = calls.add("legacy")
			override fun dispatchLegacyLive(event: MotionEvent) = calls.add("legacy-live")
			override fun dispatchPlayLikeCurl(event: MotionEvent) = calls.add("play-like-curl")
			override fun dispatchShellCover(event: MotionEvent) = calls.add("shell-cover")
			override fun dispatchLiveEngine(event: MotionEvent) = calls.add("live-engine")
		}
		val expectedByMode = mapOf(
			ReaderPagePhysicalDispatchMode.CueMap to "cue-map",
			ReaderPagePhysicalDispatchMode.ChromeOnly to "chrome-only",
			ReaderPagePhysicalDispatchMode.Denied to "denied",
			ReaderPagePhysicalDispatchMode.Legacy to "legacy",
			ReaderPagePhysicalDispatchMode.LegacyLive to "legacy-live",
			ReaderPagePhysicalDispatchMode.PlayLikeCurl to "play-like-curl",
			ReaderPagePhysicalDispatchMode.ShellCover to "shell-cover",
			ReaderPagePhysicalDispatchMode.LiveEngine to "live-engine"
		)
		val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
		try {
			expectedByMode.forEach { (mode, expected) ->
				calls.clear()
				assertTrue(
					readerDispatchPagePhysicalEvent(mode, event, target) {
						calls.add("fallback")
					}
				)
				assertEquals(listOf(expected), calls, "mode=$mode")
			}
			calls.clear()
			assertTrue(
				readerDispatchPagePhysicalEvent(null, event, target) {
					calls.add("fallback")
				}
			)
			assertEquals(listOf("fallback"), calls)
		} finally {
			event.recycle()
		}
	}

	@Test
	fun legacyLivePointerStreamRevokesChangedContextOnceAndSuppressesLateTerminal() {
		val stream = ReaderLegacyLivePointerStream()
		val initial = legacyLivePointerContext()

		stream.begin(ReaderPagePhysicalDispatchMode.LegacyLive, initial)

		assertFalse(stream.revokeIfContextChanged(initial))
		assertTrue(stream.revokeIfContextChanged(initial.copy(shellCoverVisible = true)))
		assertFalse(stream.revokeIfContextChanged(initial.copy(shellCoverVisible = true)))
		assertFalse(stream.revoke())
		assertTrue(stream.suppressesOriginalTerminal)
		stream.finish()
		assertFalse(stream.suppressesOriginalTerminal)
		assertFalse(stream.revoke())
	}

	@Test
	fun nonLegacyPointerStreamsNeverRevokeLegacyDelivery() {
		ReaderPagePhysicalDispatchMode.entries
			.filterNot { it == ReaderPagePhysicalDispatchMode.LegacyLive }
			.forEach { mode ->
				val stream = ReaderLegacyLivePointerStream()
				val context = legacyLivePointerContext()

				stream.begin(mode, context)

				assertFalse(stream.revokeIfContextChanged(context.copy(shellCoverVisible = true)))
				assertFalse(stream.revoke())
				assertFalse(stream.suppressesOriginalTerminal)
			}
	}

	@Test
	fun physicalDispatchUsesTypedTargetsAndCancelsChangedLegacyStreams() {
		val source = hostFile.readText()
		val physicalDispatch = source
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("private fun dispatchLegacyLivePointerEvent")
		val viewerContainer = source
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
		val viewerReplacement = viewerContainer
			.substringAfter("fun replaceViewerContent(viewerView: View)")
			.substringBefore("fun detachViewerContent(viewerView: View?)")
		val compatibilityUpdate = viewerContainer
			.substringAfter("fun setLegacyLiveCompatibilityContext(")
			.substringBefore("fun setPageTurnCanvasEnabled(")
		val canvasUpdate = viewerContainer
			.substringAfter("fun setPageTurnCanvasEnabled(")
			.substringBefore("fun setPageTurnReadingDirection(")
		val decisionUpdate = viewerContainer
			.substringAfter("fun setPresentationDecision(")
			.substringBefore("fun releaseStalePresentation(")
		val shellCoverUpdate = viewerContainer
			.substringAfter("fun setShellCoverVisible(")
			.substringBefore("fun setPageOperationPolicy(")
		val physicalClose = viewerContainer
			.substringAfter("private fun closePhysicalPointerDelivery()")
			.substringBefore("private fun teardownTask4Resources()")

		assertContains(source, "ReaderPagePhysicalDispatchMode.Denied")
		assertContains(source, "ReaderPagePhysicalDispatchMode.ShellCover")
		assertContains(source, "ReaderPagePhysicalDispatchMode.LegacyLive")
		assertContains(source, "legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext")
		assertContains(source, "internal interface ReaderPagePhysicalDispatchTarget")
		assertContains(source, "internal fun readerDispatchPagePhysicalEvent(")
		assertContains(source, "internal class ReaderLegacyLivePointerStream")
		assertContains(source, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(source, "physicalDispatchMode = ReaderPagePhysicalDispatchMode.Denied")
		assertContains(source, "action = MotionEvent.ACTION_CANCEL")
		assertContains(physicalDispatch, "legacyLivePointerStream.begin(")
		assertContains(physicalDispatch, "legacyLivePointerStream.suppressesOriginalTerminal")
		assertContains(physicalDispatch, "legacyLivePointerStream.finish()")
		assertContains(viewerReplacement, "cancelLegacyLivePointerStream()")
		assertContains(compatibilityUpdate, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(canvasUpdate, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(decisionUpdate, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(shellCoverUpdate, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(physicalClose, "cancelLegacyLivePointerStream()")
		assertContains(physicalClose, "legacyLivePointerStream.suppressesOriginalTerminal")
		assertFalse(
			source.contains(
				"if (!pageTurnCanvasEnabled) return ReaderPagePhysicalDispatchMode.LiveEngine"
			)
		)
	}

	@Test
	fun shellCoverIsDrawnBehindPredecessorUntilCommittedAuthoritySelectsIt() {
		val source = hostFile.readText()
		val preparation = source
			.substringAfter("fun prepareShellCoverForCommit(")
			.substringBefore("fun cancelShellCoverCommitPreparation(")
		val finalSelection = source
			.substringAfter("fun selectShellCover(")
			.substringBefore("fun setVerticalPageDragPreview(")
		val proofRetention = source
			.substringAfter("fun setShellCoverVisible(")
			.substringBefore("fun setPageOperationPolicy(")

		assertContains(preparation, "addView(shellCoverView, 0")
		assertContains(preparation, "shellCoverView.visibility = VISIBLE")
		assertContains(preparation, "shellCoverView.isClickable = false")
		assertFalse(preparation.contains("invalidate(\"shell-cover-visible\")"))
		assertContains(finalSelection, "shellCoverView.bringToFront()")
		assertContains(finalSelection, "preserveNativePresentationProof")
		assertContains(
			proofRetention,
			"if (visible && !preserveNativePresentationProof)"
		)
		assertContains(proofRetention, "playLikeCurlController.invalidate(\"shell-cover-visible\")")
		assertContains(
			proofRetention,
			"pageRasterPreparationController.invalidate(\"shell-cover-visible\")"
		)
	}

	private fun legacyLivePointerContext() = ReaderLegacyLivePointerContext(
		pageTurnCanvasEnabled = false,
		presentationDecision = null,
		compatibilityContext = coldLegacyLiveCompatibilityContext(),
		shellCoverVisible = false
	)

	private fun coldLegacyLiveCompatibilityContext() =
		ReaderLegacyLiveCompatibilityContext.ColdSession(
			identity = ReaderLegacyLiveCompatibilityIdentity(
				readerSessionGeneration = 1L,
				publication = ReaderPublicationIdentity(
					bookId = "book-1",
					title = "Book",
					resourceHref = "book.epub",
					kind = ReaderPublicationKind.Ebook,
					format = ReaderPublicationFormat.Epub
				),
				foliateSessionId = null,
				destinationCommitIdentity = null
			),
			lifecycle = ReaderPresentationLifecycleState.Foreground
		)
}
