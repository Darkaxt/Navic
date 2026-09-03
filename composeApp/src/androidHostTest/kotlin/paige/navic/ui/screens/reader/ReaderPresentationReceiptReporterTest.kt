package paige.navic.ui.screens.reader

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.ReaderController
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderNativePagePresentationRequest
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderViewerAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ReaderPresentationReceiptReporterTest {
	@Test
	fun delayedNativeProofChainsPartialAndCompleteFactsOnlyFromReceiptTruth() {
		val completeA = completeBinding("delayed-receipt-session", destination = 4L)
		val partialB = completeA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"delayed-receipt-session",
				5L
			),
			rasterGeneration = null,
			textureGeneration = null
		)
		val completeB = partialB.copy(rasterGeneration = 8L, textureGeneration = 9L)
		val request = ReaderNativePagePresentationRequest(
			ReaderPresentationToken(10L),
			completeA
		)
		val cachedState = ReaderPresentationState(
			authority = ReaderPresentationAuthority.BlockingPreparation(
				ReaderPresentationFrameOwner.Neutral,
				request
			),
			binding = completeA,
			nextTokenValue = 11L
		)
		val reporter = ReaderPresentationBindingReporter()
		assertNull(reporter.update(completeA, completeA, false, false))
		var controller = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 20L,
				presentation = cachedState
			)
		)
		val proofA = ReaderNativePagePresentationProof(
			binding = completeA,
			transitionToken = request.token,
			presentedFrame = 11L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 5L,
			textureGeneration = 6L
		)
		controller = dispatchAndConsume(
			reporter,
			controller,
			ReaderPresentationEvent.NativePagePresented(proofA)
		).first

		val partialEvent = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(completeA, partialB, false, true)
		)
		val rejected = dispatchAndConsume(reporter, controller, partialEvent)
		controller = rejected.first
		assertEquals(ReaderPresentationEventDisposition.Rejected, rejected.second.disposition)
		assertEquals(completeA, reporter.lastReportedBinding)

		val completeEvent = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(completeA, completeB, false, true)
		)
		assertEquals(completeB, completeEvent.binding)
		val accepted = dispatchAndConsume(reporter, controller, completeEvent)
		assertEquals(ReaderPresentationEventDisposition.Accepted, accepted.second.disposition)
		assertEquals(completeB, reporter.lastReportedBinding)

		assertNull(reporter.update(completeA, completeB, false, true))
		assertEquals(completeB, reporter.lastReportedBinding)
	}

	@Test
	fun publicationOpenReceiptImmediatelyAuthorizesNativeAndCoverFollowUpFacts() {
		val bindingA = completeBinding("startup-receipt-session", destination = 4L)
		val bindingB = bindingA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"startup-receipt-session",
				5L
			),
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		listOf(false, true).forEachIndexed { index, shellCoverVisible ->
			val reporter = ReaderPresentationBindingReporter()
			var controller = ReaderController(
				ReaderControllerState(
					readerSessionGeneration = 30L + index,
					shellCoverVisible = shellCoverVisible,
					presentation = ReaderPresentationState(nextTokenValue = 10L)
				)
			)
			val opened = assertIs<ReaderPresentationEvent.PublicationOpened>(
				reporter.update(null, bindingA, true, false)
			)
			val startup = dispatchAndConsume(reporter, controller, opened)
			controller = startup.first
			if (shellCoverVisible) {
				val commitPending = assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(
					startup.second.postState.authority
				)
				assertEquals(ReaderPresentationToken(10L), commitPending.token)
				val committed = dispatchAndConsume(
					reporter,
					controller,
					ReaderPresentationEvent.ShellCoverCommitted(
						ReaderShellCoverCommitProof(
							token = commitPending.token,
							binding = bindingA,
							coverGeneration = commitPending.coverGeneration,
							presentedFrame = 11L,
							viewportWidth = 1200,
							viewportHeight = 800
						)
					)
				)
				controller = committed.first
				val callbackEpoch = reporter.captureEpoch()
				val dismissed = controller.onViewerAction(
					ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
				)
				val receipt = assertNotNull(dismissed.presentationReceipt)
				assertTrue(
					reporter.consumeReceipt(
						callbackEpoch,
						ReaderPresentationEvent.ShellCoverDismissalRequested,
						receipt
					)
				)
				controller = dismissed.controller
				val preparation = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
					receipt.postState.authority
				)
				assertEquals(ReaderPresentationToken(11L), preparation.nativePresentationRequest?.token)
			} else {
				val preparation = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
					startup.second.postState.authority
				)
				assertEquals(ReaderPresentationToken(10L), preparation.nativePresentationRequest?.token)
			}

			val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
				reporter.update(null, bindingB, false, true)
			)
			val followUp = dispatchAndConsume(reporter, controller, relocation)
			assertEquals(bindingB, followUp.second.postState.binding)
			assertEquals(bindingB, reporter.lastReportedBinding)
		}
	}

	@Test
	fun shellCoverCommitAndDismissalIgnoreStaleComposeDecision() {
		val binding = completeBinding("cover-receipt-session", destination = 4L)
		val reporter = ReaderPresentationBindingReporter()
		val opened = assertNotNull(reporter.update(null, binding, true, false))
		var controller = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 40L,
				shellCoverVisible = true,
				presentation = ReaderPresentationState(nextTokenValue = 10L)
			)
		)
		controller = dispatchAndConsume(reporter, controller, opened).first
		val commitPending = assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(
			controller.state.presentation.authority
		)
		val proof = ReaderShellCoverCommitProof(
			token = commitPending.token,
			binding = binding,
			coverGeneration = commitPending.coverGeneration,
			presentedFrame = 11L,
			viewportWidth = 1200,
			viewportHeight = 800
		)
		controller = dispatchAndConsume(
			reporter,
			controller,
			ReaderPresentationEvent.ShellCoverCommitted(proof)
		).first
		val callbackEpoch = reporter.captureEpoch()
		val dismissed = controller.onViewerAction(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		)
		val dismissalReceipt = assertNotNull(dismissed.presentationReceipt)
		assertTrue(
			reporter.consumeReceipt(
				callbackEpoch,
				ReaderPresentationEvent.ShellCoverDismissalRequested,
				dismissalReceipt
			)
		)
		assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			dismissalReceipt.postState.authority
		)

		assertNull(reporter.update(binding, binding, false, false))
		assertEquals(binding, reporter.lastReportedBinding)
	}

	@Test
	fun absentThrowingAndMismatchedCallbacksLeaveCandidateRetryable() {
		val bindingA = completeBinding("retry-receipt-session", destination = 4L)
		val bindingB = bindingA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"retry-receipt-session",
				5L
			),
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		val reporter = ReaderPresentationBindingReporter()
		assertNull(reporter.update(bindingA, bindingA, false, false))
		val event = assertNotNull(reporter.update(bindingA, bindingB, false, true))
		val epoch = reporter.captureEpoch()

		assertFalse(reporter.consumeReceipt(epoch, event, null))
		assertEquals(event, reporter.update(bindingA, bindingB, false, true))
		runCatching { error("callback failed") }
		assertEquals(event, reporter.update(bindingA, bindingB, false, true))

		val controllerReceipt = assertNotNull(
			ReaderController(
				ReaderControllerState(
					readerSessionGeneration = 50L,
					presentation = settledState(bindingA)
				)
			).onPresentationEvent(event).presentationReceipt
		)
		assertFalse(
			reporter.consumeReceipt(
				epoch,
				ReaderPresentationEvent.Lifecycle(
					ReaderPresentationLifecycleEvent.RendererLost
				),
				controllerReceipt
			)
		)
		assertEquals(event, reporter.update(bindingA, bindingB, false, true))
	}

	@Test
	fun epochSessionSequenceLifecycleAndComposeEchoesFenceStaleReceipts() {
		val bindingA = completeBinding("fence-receipt-session", destination = 4L)
		val bindingB = bindingA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"fence-receipt-session",
				5L
			),
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		val reporter = ReaderPresentationBindingReporter()
		assertNull(reporter.update(bindingA, bindingA, false, false))
		var controller = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 60L,
				presentation = settledState(bindingA)
			)
		)
		val relocation = assertNotNull(reporter.update(bindingA, bindingB, false, true))
		val relocated = dispatchAndConsume(reporter, controller, relocation)
		controller = relocated.first
		assertEquals(bindingB, reporter.lastReportedBinding)
		assertNull(reporter.update(bindingA, bindingB, false, true))

		val rendererLost = ReaderPresentationEvent.Lifecycle(
			ReaderPresentationLifecycleEvent.RendererLost
		)
		val lost = dispatchAndConsume(reporter, controller, rendererLost)
		controller = lost.first
		val oldVersion = relocated.second
		assertFalse(
			reporter.consumeReceipt(
				reporter.captureEpoch(),
				relocation,
				oldVersion
			)
		)
		listOf(59L, 61L).forEach { mismatchedSession ->
			val wrongSessionReceipt = oldVersion.copy(
				version = ReaderPresentationReceiptVersion(
					readerSessionGeneration = mismatchedSession,
					eventSequence = 100L
				)
			)
			assertFalse(
				reporter.consumeReceipt(
					reporter.captureEpoch(),
					relocation,
					wrongSessionReceipt
				)
			)
		}

		val background = dispatchAndConsume(
			reporter,
			controller,
			ReaderPresentationEvent.Lifecycle(
				ReaderPresentationLifecycleEvent.VisibilityLost
			)
		)
		controller = background.first
		assertNull(reporter.update(bindingA, bindingB, false, true))
		val destroyed = dispatchAndConsume(
			reporter,
			controller,
			ReaderPresentationEvent.Lifecycle(
				ReaderPresentationLifecycleEvent.PublicationClosed
			)
		)
		assertEquals(
			ReaderPresentationLifecycleState.Destroyed,
			destroyed.second.postState.lifecycle
		)
		assertNull(reporter.update(bindingA, bindingB, false, true))

		val staleEpoch = reporter.captureEpoch()
		reporter.reset()
		assertFalse(reporter.consumeReceipt(staleEpoch, rendererLost, lost.second))
		assertNull(reporter.lastReportedBinding)
	}

	private fun dispatchAndConsume(
		reporter: ReaderPresentationBindingReporter,
		controller: ReaderController,
		event: ReaderPresentationEvent
	): Pair<ReaderController, ReaderPresentationEventReceipt> {
		val epoch = reporter.captureEpoch()
		val step = controller.onPresentationEvent(event)
		val receipt = assertNotNull(step.presentationReceipt)
		assertTrue(reporter.consumeReceipt(epoch, event, receipt))
		return step.controller to receipt
	}

	private fun completeBinding(
		session: String,
		destination: Long
	): ReaderPresentationBinding = ReaderPresentationBinding(
		foliateSessionId = session,
		publicationGeneration = 1L,
		viewportGeneration = 2L,
		profileGeneration = 3L,
		destinationCommitIdentity = ReaderDestinationCommitIdentity(session, destination),
		rasterGeneration = 5L,
		textureGeneration = 6L,
		preparationGeneration = 7L
	)

	private fun settledState(binding: ReaderPresentationBinding): ReaderPresentationState {
		val proof = ReaderNativePagePresentationProof(
			binding = binding,
			transitionToken = null,
			presentedFrame = 8L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = assertNotNull(binding.rasterGeneration),
			textureGeneration = assertNotNull(binding.textureGeneration)
		)
		return ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proof)
			),
			binding = binding
		)
	}
}
