package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
