package paige.navic.reader

import java.io.File

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderPresentationViewerActionPolicyTest {
	private val acceptedPagePolicy = ReaderPageOperationPolicy(
		newPointer = ReaderPageNewPointerDecision.Accept,
		continueActivePointer = true,
		continueSettlement = false
	)
	private val rejectedPagePolicy = ReaderPageOperationPolicy(
		newPointer = ReaderPageNewPointerDecision.Reject(
			ReaderPageGestureTerminalOutcome.RejectedPreparing
		),
		continueActivePointer = false,
		continueSettlement = false
	)
	private val actions = listOf(
		ReaderViewerAction.Menu,
		ReaderViewerAction.NativeShellPrepared,
		ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous),
		ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
		ReaderViewerAction.PreviewPageDrag(deltaX = 1.0),
		ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Up),
		ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Down),
		ReaderViewerAction.NavigateTo(ReaderLocator(progress = 0.5)),
		ReaderViewerAction.ContentLongPressAt(x = 1.0, y = 2.0)
	)

	@Test
	fun everyViewerActionIsAdmittedOnlyByItsPresentationPolicy() {
		val allExceptNativeShell = actions.toSet() - ReaderViewerAction.NativeShellPrepared
		val expectedByPolicy = listOf(
			ReaderPresentationInputPolicy.RecoveryOnly to emptySet(),
			ReaderPresentationInputPolicy.ChromeOnly to setOf(ReaderViewerAction.Menu),
			ReaderPresentationInputPolicy.ShellCover to setOf(
				ReaderViewerAction.Menu,
				ReaderViewerAction.NativeShellPrepared,
				ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
				ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Down)
			),
			ReaderPresentationInputPolicy.ClaimedCurl(ReaderPresentationToken(1L)) to emptySet(),
			ReaderPresentationInputPolicy.NativePage(acceptedPagePolicy) to allExceptNativeShell,
			ReaderPresentationInputPolicy.NativePage(rejectedPagePolicy) to setOf(ReaderViewerAction.Menu),
			ReaderPresentationInputPolicy.LiveEngine to allExceptNativeShell
		)

		expectedByPolicy.forEach { (policy, admitted) ->
			actions.forEach { action ->
				assertEquals(
					action in admitted,
					readerViewerActionIsAdmitted(policy, action),
					"policy=$policy action=$action"
				)
			}
		}
	}

	@Test
	fun controllerAdmissionChecksLifecycleBeforeEveryPolicyAndViewerIntent() {
		val binding = ReaderPresentationBinding(
			foliateSessionId = "lifecycle-session",
			publicationGeneration = 1L,
			viewportGeneration = 1L,
			profileGeneration = 1L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"lifecycle-session",
				1L
			),
			rasterGeneration = 2L,
			textureGeneration = 3L,
			preparationGeneration = 4L
		)
		val coverProof = ReaderShellCoverCommitProof(
			token = ReaderPresentationToken(5L),
			binding = binding,
			coverGeneration = 6L,
			presentedFrame = 7L,
			viewportWidth = 100,
			viewportHeight = 200
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding = binding,
			transitionToken = null,
			presentedFrame = 8L,
			viewportWidth = 100,
			viewportHeight = 200,
			rasterGeneration = 2L,
			textureGeneration = 3L
		)
		val curlFrame = ReaderPresentationFrameOwner.Curl(
			ReaderCurlPresentationFrame(
				token = ReaderPresentationToken(9L),
				binding = binding,
				presentedFrame = 10L,
				viewportWidth = 100,
				viewportHeight = 200,
				rasterGeneration = 2L,
				textureGeneration = 3L
			)
		)
		val liveFrame = ReaderPresentationFrameOwner.LiveEngine(
			ReaderLiveEnginePresentationProof(
				token = ReaderPresentationToken(11L),
				binding = binding,
				presentedFrameSequence = 12L
			)
		)
		val readyFacts = ReaderPagePreparationFacts(
			phase = ReaderPagePreparationPhase.Ready,
			generation = 4L,
			completedCount = 1,
			requiredCount = 1,
			readiness = ReaderPageReadinessState(
				rasterGeneration = ReaderChapterRasterGenerationState.Ready,
				decodedWorkingSet = ReaderDecodedWorkingSetState.Ready,
				textureDeck = ReaderTextureDeckState.Ready,
				interaction = ReaderPageInteractionState.Ready
			)
		)
		val policyStates = listOf(
			ReaderPresentationInputPolicy.RecoveryOnly to ReaderPresentationState(),
			ReaderPresentationInputPolicy.ChromeOnly to ReaderPresentationState(
				authority = ReaderPresentationAuthority.BlockingPreparation(
					ReaderPresentationFrameOwner.ShellCover(coverProof)
				),
				binding = binding
			),
			ReaderPresentationInputPolicy.ShellCover to ReaderPresentationState(
				authority = ReaderPresentationAuthority.ShellCover(coverProof),
				binding = binding
			),
			ReaderPresentationInputPolicy.ClaimedCurl(ReaderPresentationToken(9L)) to
				ReaderPresentationState(
					authority = ReaderPresentationAuthority.CurlGesture(curlFrame),
					binding = binding
				),
			ReaderPresentationInputPolicy.NativePage(acceptedPagePolicy) to
				ReaderPresentationState(
					authority = ReaderPresentationAuthority.SettledNativePage(
						ReaderPresentationFrameOwner.NativePage(nativeProof)
					),
					binding = binding,
					preparationFacts = readyFacts
				),
			ReaderPresentationInputPolicy.NativePage(rejectedPagePolicy) to
				ReaderPresentationState(
					authority = ReaderPresentationAuthority.SettledNativePage(
						ReaderPresentationFrameOwner.NativePage(nativeProof)
					),
					binding = binding
				),
			ReaderPresentationInputPolicy.LiveEngine to ReaderPresentationState(
				authority = ReaderPresentationAuthority.LiveEngineExposed(liveFrame),
				binding = binding
			)
		)
		val scenarios = listOf(
			Triple("menu", false, ReaderViewerAction.Menu),
			Triple("close", true, ReaderViewerAction.Menu),
			Triple(
				"next",
				false,
				ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
			),
			Triple(
				"previous",
				false,
				ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)
			)
		)

		policyStates.forEach { (policy, presentation) ->
			assertEquals(policy, readerPresentationDecision(presentation).inputPolicy)
			scenarios.forEach { (intent, menuVisible, action) ->
				val foreground = ReaderController(
					state = ReaderControllerState(
						presentation = presentation,
						menuVisible = menuVisible,
						lastContentActionClaim = ReaderContentActionClaim()
					)
				)
				val admitted = foreground.onViewerAction(action)
				val producedOutcome = admitted.controller != foreground ||
					admitted.engineCommands.isNotEmpty() ||
					admitted.presentationEffects.isNotEmpty()
				assertEquals(
					readerViewerActionIsAdmitted(policy, action),
					producedOutcome,
					"lifecycle=Foreground policy=$policy intent=$intent"
				)

				listOf(
					ReaderPresentationLifecycleState.Background,
					ReaderPresentationLifecycleState.Destroyed
				).forEach { lifecycle ->
					val denied = foreground.copy(
						state = foreground.state.copy(
							presentation = presentation.copy(lifecycle = lifecycle)
						)
					)
					val step = denied.onViewerAction(action)
					assertEquals(
						denied,
						step.controller,
						"lifecycle=$lifecycle policy=$policy intent=$intent"
					)
					assertTrue(step.engineCommands.isEmpty())
					assertTrue(step.presentationEffects.isEmpty())
				}
			}
		}
	}

	@Test
	fun delayedViewerCallbacksRecheckCurrentLifecycleBeforeColdCompatibility() {
		val foreground = ReaderController().open(openRequest()).controller
		val context = assertIs<ReaderLegacyLiveCompatibilityContext.ColdSession>(
			ReaderLegacyLiveCompatibilityGate().resolve(
				state = foreground.state,
				pageTurnCanvasEnabled = false
			)
		)

		listOf(
			ReaderPresentationLifecycleState.Background,
			ReaderPresentationLifecycleState.Destroyed
		).forEach { lifecycle ->
			val revoked = foreground.copy(
				state = foreground.state.copy(
					presentation = foreground.state.presentation.copy(lifecycle = lifecycle)
				)
			)
			listOf(
				ReaderViewerAction.Menu,
				ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
				ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)
			).forEach { action ->
				val step = revoked.onViewerAction(action, context)
				assertEquals(revoked, step.controller, "lifecycle=$lifecycle action=$action")
				assertTrue(step.engineCommands.isEmpty())
				assertTrue(step.presentationEffects.isEmpty())
			}
		}
	}

	@Test
	fun deniedViewerActionCannotClearAContentClaimOrMutateControllerState() {
		val controller = ReaderController(
			state = ReaderControllerState(
				lastContentActionClaim = ReaderContentActionClaim()
			)
		)

		val denied = controller.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)

		assertEquals(controller, denied.controller)
		assertTrue(denied.engineCommands.isEmpty())
		assertTrue(denied.presentationEffects.isEmpty())
	}

	@Test
	fun admittedViewerActionRunsOnlyAfterPolicyAdmission() {
		val partialBinding = ReaderPresentationBinding(
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 1L,
			profileGeneration = 1L,
			preparationGeneration = 1L
		)
		val controller = ReaderController(
			state = ReaderControllerState(
				presentation = ReaderPresentationState(
					authority = ReaderPresentationAuthority.ShellCoverCommitPending(
						retainedFrame = ReaderShellCoverRetainedFrame.Neutral(partialBinding),
						token = ReaderPresentationToken(1L),
						binding = partialBinding,
						coverGeneration = 1L
					),
					binding = partialBinding
				),
				lastContentActionClaim = ReaderContentActionClaim()
			)
		)

		val admitted = controller.onViewerAction(ReaderViewerAction.Menu)

		assertTrue(admitted.controller.state.menuVisible)
		assertFalse(admitted.controller.state.lastContentActionClaim != null)
	}

	@Test
	fun exactColdLegacyContextAdmitsRecoveryActionsAndRejectsEveryFence() {
		val opened = ReaderController().open(openRequest()).controller
		val context = assertIs<ReaderLegacyLiveCompatibilityContext.ColdSession>(
			ReaderLegacyLiveCompatibilityGate().resolve(
				state = opened.state,
				pageTurnCanvasEnabled = false
			)
		)

		assertTrue(
			readerViewerActionIsAdmitted(
				ReaderPresentationInputPolicy.RecoveryOnly,
				ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
				context
			)
		)
		assertFalse(
			readerViewerActionIsAdmitted(
				ReaderPresentationInputPolicy.RecoveryOnly,
				ReaderViewerAction.NativeShellPrepared,
				context
			)
		)
		assertTrue(
			opened.onViewerAction(
				ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
				context
			).engineCommands.single() is ReaderEngineCommand.TurnPage
		)

		val binding = ReaderPresentationBinding(
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 1L,
			profileGeneration = 1L,
			preparationGeneration = 1L
		)
		val fencedStates = listOf(
			opened.state.copy(shellCoverVisible = true),
			opened.state.copy(nativeShellCoverUrl = "cover://intent"),
			opened.state.copy(
				presentation = opened.state.presentation.copy(
					lifecycle = ReaderPresentationLifecycleState.Background
				)
			),
			opened.state.copy(
				presentation = opened.state.presentation.copy(binding = binding)
			),
			opened.state.copy(
				presentation = opened.state.presentation.copy(
					authority = ReaderPresentationAuthority.BlockingPreparation(
						ReaderPresentationFrameOwner.Neutral
					),
					binding = binding
				)
			)
		)
		fencedStates.forEach { fenced ->
			assertIs<ReaderLegacyLiveCompatibilityContext.Denied>(
				ReaderLegacyLiveCompatibilityGate().resolve(fenced, false),
				"state=$fenced"
			)
		}
		assertIs<ReaderLegacyLiveCompatibilityContext.Denied>(
			ReaderLegacyLiveCompatibilityGate().resolve(opened.state, true)
		)
	}

	@Test
	fun ownershipRetiresCompatibilityUntilANewColdReaderSession() {
		val request = openRequest()
		val opened = ReaderController().open(request).controller
		val gate = ReaderLegacyLiveCompatibilityGate()
		assertIs<ReaderLegacyLiveCompatibilityContext.ColdSession>(
			gate.resolve(opened.state, false)
		)

		val binding = ReaderPresentationBinding(
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 1L,
			profileGeneration = 1L,
			preparationGeneration = 1L
		)
		val ownershipStarted = opened.state.copy(
			presentation = opened.state.presentation.copy(
				authority = ReaderPresentationAuthority.BlockingPreparation(
					ReaderPresentationFrameOwner.Neutral
				),
				binding = binding
			)
		)
		assertIs<ReaderLegacyLiveCompatibilityContext.Denied>(
			gate.resolve(ownershipStarted, false)
		)
		assertIs<ReaderLegacyLiveCompatibilityContext.Denied>(
			gate.resolve(opened.state, false)
		)

		val reopened = opened.open(request).controller
		assertEquals(1L, opened.state.readerSessionGeneration)
		assertEquals(2L, reopened.state.readerSessionGeneration)
		assertIs<ReaderLegacyLiveCompatibilityContext.ColdSession>(
			gate.resolve(reopened.state, false)
		)
		assertIs<ReaderLegacyLiveCompatibilityContext.Denied>(
			gate.resolve(opened.state, false)
		)
		assertIs<ReaderLegacyLiveCompatibilityContext.ColdSession>(
			gate.resolve(reopened.state, false)
		)
	}

	@Test
	fun compatibilityContextIsExactAndCannotOverrideOtherPolicies() {
		val opened = ReaderController().open(openRequest()).controller
		val gate = ReaderLegacyLiveCompatibilityGate()
		val context = assertIs<ReaderLegacyLiveCompatibilityContext.ColdSession>(
			gate.resolve(opened.state, false)
		)
		val transitioned = opened.copy(
			state = opened.state.copy(
				foliateSessionId = "fixture-session",
				destinationCommitIdentity = ReaderDestinationCommitIdentity(
					"fixture-session",
					1L
				)
			)
		)
		val transitionedContext = assertIs<ReaderLegacyLiveCompatibilityContext.ColdSession>(
			gate.resolve(transitioned.state, false)
		)
		assertFalse(context == transitionedContext)
		assertEquals(
			transitioned,
			transitioned.onViewerAction(
				ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
				context
			).controller
		)
		assertTrue(
			transitioned.onViewerAction(
				ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
				transitionedContext
			).engineCommands.single() is ReaderEngineCommand.TurnPage
		)
		assertFalse(
			readerViewerActionIsAdmitted(
				ReaderPresentationInputPolicy.ClaimedCurl(ReaderPresentationToken(1L)),
				ReaderViewerAction.Menu,
				transitionedContext
			)
		)
		assertFalse(
			readerViewerActionIsAdmitted(
				ReaderPresentationInputPolicy.NativePage(rejectedPagePolicy),
				ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
				transitionedContext
			)
		)
	}

	@Test
	fun typedColdLegacyCompatibilityIsSharedByPointerAndViewerActions() {
		val controller = sourceFile(
			"src/commonMain/kotlin/paige/navic/reader/ReaderPresentationController.kt"
		)
		val state = sourceFile(
			"src/commonMain/kotlin/paige/navic/reader/ReaderControllerState.kt"
		)
		val readerController = sourceFile(
			"src/commonMain/kotlin/paige/navic/reader/ReaderController.kt"
		)
		val screen = sourceFile(
			"src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		)
		val root = sourceFile(
			"src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		)
		val commonHost = sourceFile(
			"src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		)
		val androidHost = sourceFile(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		)
		val iosHost = sourceFile(
			"src/iosMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.ios.kt"
		)

		assertTrue(controller.contains("sealed interface ReaderLegacyLiveCompatibilityContext"))
		assertTrue(controller.contains("class ReaderLegacyLiveCompatibilityGate"))
		assertTrue(controller.contains("legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext"))
		assertFalse(controller.contains("legacyLiveCompatibilityGranted: Boolean"))
		assertTrue(state.contains("val readerSessionGeneration: Long = 0L"))
		assertTrue(readerController.contains("readerSessionGeneration = Math.incrementExact"))
		assertTrue(screen.contains("val pageTurnCanvasEnabled ="))
		assertTrue(screen.contains("legacyLiveCompatibilityGate.resolve("))
		assertTrue(screen.contains("legacyLiveCompatibilityContext = legacyLiveCompatibilityContext"))
		assertTrue(screen.contains("onViewerAction(action, legacyLiveCompatibilityContext)"))
		assertTrue(root.contains("legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext"))
		listOf(commonHost, androidHost, iosHost).forEach { source ->
			assertTrue(
				source.contains(
					"legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext"
				)
			)
		}
	}

	private fun openRequest() = ReaderEngineOpenRequest(
		publication = ReaderPublicationIdentity(
			bookId = "book-1",
			title = "Book",
			resourceHref = "book.epub",
			kind = ReaderPublicationKind.Ebook,
			format = ReaderPublicationFormat.Epub
		),
		url = "https://example.invalid/book.epub"
	)

	private fun sourceFile(relativePath: String): String = listOf(
		File(relativePath),
		File("composeApp/$relativePath")
	).firstOrNull(File::isFile)?.readText() ?: error("Could not locate $relativePath")
}
