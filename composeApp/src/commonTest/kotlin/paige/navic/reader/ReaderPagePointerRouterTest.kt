package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPagePointerRouterTest {
	@Test
	fun acceptedHorizontalSequenceClaimsCurlOnceAndPublishesOneTerminal() {
		val lifecycle = ReaderPageGestureLifecycle()
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val router = ReaderPagePointerRouter(lifecycle) { gestureId, outcome ->
			published += gestureId to outcome
		}
		val begin = router.begin(100f, 200f, ReaderPageNewPointerDecision.Accept)
		val gestureId = begin.gestureId

		assertEquals(ReaderPagePointerRoute.Content, begin.route)
		assertEquals(ReaderPagePointerRoute.Content, router.move(106f, 202f, 8f))
		assertEquals(ReaderPagePointerRoute.ClaimCurl(gestureId), router.move(120f, 203f, 8f))
		assertEquals(ReaderPagePointerRoute.Curl(gestureId), router.move(140f, 204f, 8f))
		assertTrue(router.complete(gestureId, ReaderPageGestureTerminalOutcome.CommittedForward))
		assertFalse(router.complete(gestureId, ReaderPageGestureTerminalOutcome.CancelledLifecycle))
		assertEquals(
			listOf(gestureId to ReaderPageGestureTerminalOutcome.CommittedForward),
			published
		)
		assertEquals(0, router.trackedSequenceCount())
	}

	@Test
	fun completedActiveGestureConsumesPhysicalTailUntilUp() {
		val lifecycle = ReaderPageGestureLifecycle()
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val router = ReaderPagePointerRouter(lifecycle) { gestureId, outcome ->
			published += gestureId to outcome
		}
		val gestureId = router.begin(100f, 200f, ReaderPageNewPointerDecision.Accept).gestureId
		assertEquals(ReaderPagePointerRoute.ClaimCurl(gestureId), router.move(120f, 200f, 8f))

		assertTrue(router.complete(gestureId, ReaderPageGestureTerminalOutcome.FailedRenderer))
		assertEquals(ReaderPagePointerRoute.Consume, router.move(140f, 200f, 8f))
		assertEquals(ReaderPagePointerRoute.Consume, router.pointerUp(gestureId))
		assertEquals(ReaderPagePointerRoute.Ignore, router.pointerUp(gestureId))
		assertFalse(router.complete(gestureId, ReaderPageGestureTerminalOutcome.CancelledLifecycle))
		assertEquals(
			listOf(gestureId to ReaderPageGestureTerminalOutcome.FailedRenderer),
			published
		)
	}

	@Test
	fun verticalContentUpPublishesCancelledByUserWithoutHostTapFlags() {
		val lifecycle = ReaderPageGestureLifecycle()
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val router = ReaderPagePointerRouter(lifecycle) { gestureId, outcome ->
			published += gestureId to outcome
		}
		val gestureId = router.begin(20f, 20f, ReaderPageNewPointerDecision.Accept).gestureId

		assertEquals(ReaderPagePointerRoute.Content, router.move(22f, 50f, 8f))
		assertEquals(
			ReaderPagePointerRoute.ContentTerminal(
				gestureId,
				ReaderPageGestureTerminalOutcome.CancelledByUser
			),
			router.pointerUp(gestureId)
		)
		assertTrue(router.complete(gestureId, ReaderPageGestureTerminalOutcome.CancelledByUser))
		assertEquals(
			listOf(gestureId to ReaderPageGestureTerminalOutcome.CancelledByUser),
			published
		)
	}

	@Test
	fun rejectedDownConsumesEntireStreamAndPublishesExactlyOnce() {
		listOf(
			ReaderPageGestureTerminalOutcome.RejectedPreparing,
			ReaderPageGestureTerminalOutcome.RejectedSettling,
			ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable
		).forEach { outcome ->
			val lifecycle = ReaderPageGestureLifecycle()
			val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
			val router = ReaderPagePointerRouter(lifecycle) { gestureId, terminal ->
				published += gestureId to terminal
			}
			val begin = router.begin(100f, 200f, ReaderPageNewPointerDecision.Reject(outcome))

			assertEquals(ReaderPagePointerRoute.Terminal(begin.gestureId, outcome), begin.route)
			assertEquals(outcome, lifecycle.terminalOutcome(begin.gestureId))
			assertEquals(ReaderPagePointerRoute.Consume, router.move(101f, 201f, 8f))
			assertEquals(ReaderPagePointerRoute.Consume, router.pointerUp(begin.gestureId))
			assertFalse(router.complete(begin.gestureId, ReaderPageGestureTerminalOutcome.FailedRenderer))
			assertEquals(listOf(begin.gestureId to outcome), published)
			assertEquals(0, router.trackedSequenceCount())
		}
	}

	@Test
	fun longPressBeforeUpClaimsContentAndPublishesExactlyOnceAtStreamEnd() {
		val lifecycle = ReaderPageGestureLifecycle()
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val router = ReaderPagePointerRouter(lifecycle) { gestureId, outcome ->
			published += gestureId to outcome
		}
		val gestureId = router.begin(20f, 20f, ReaderPageNewPointerDecision.Accept).gestureId

		assertEquals(ReaderPagePointerRoute.Content, router.claimContentAction(gestureId))
		assertEquals(ReaderPagePointerRoute.Content, router.move(80f, 20f, 8f))
		assertEquals(
			ReaderPagePointerRoute.ContentTerminal(
				gestureId,
				ReaderPageGestureTerminalOutcome.CompletedTapAction
			),
			router.pointerUp(gestureId)
		)
		assertTrue(router.complete(gestureId, ReaderPageGestureTerminalOutcome.CompletedTapAction))
		assertFalse(router.complete(gestureId, ReaderPageGestureTerminalOutcome.CancelledByUser))
		assertEquals(
			listOf(gestureId to ReaderPageGestureTerminalOutcome.CompletedTapAction),
			published
		)
	}

	@Test
	fun androidCancelAfterCompletedLongPressKeepsContentOutcome() {
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val router = ReaderPagePointerRouter(ReaderPageGestureLifecycle()) { gestureId, outcome ->
			published += gestureId to outcome
		}
		val gestureId = router.begin(20f, 20f, ReaderPageNewPointerDecision.Accept).gestureId
		assertEquals(ReaderPagePointerRoute.Content, router.claimContentAction(gestureId))

		assertEquals(
			ReaderPagePointerRoute.Terminal(
				gestureId,
				ReaderPageGestureTerminalOutcome.CompletedTapAction
			),
			router.interruptPhysicalStream(gestureId, finalStreamEvent = true)
		)
		assertEquals(
			listOf(gestureId to ReaderPageGestureTerminalOutcome.CompletedTapAction),
			published
		)
	}

	@Test
	fun lifecycleCancellationCanWinBeforeLongPressStreamEnds() {
		val router = ReaderPagePointerRouter(ReaderPageGestureLifecycle())
		val gestureId = router.begin(20f, 20f, ReaderPageNewPointerDecision.Accept).gestureId
		assertEquals(ReaderPagePointerRoute.Content, router.claimContentAction(gestureId))

		assertEquals(
			listOf(gestureId),
			router.cancelAll(ReaderPageGestureTerminalOutcome.CancelledLifecycle)
		)
		assertEquals(ReaderPagePointerRoute.Consume, router.pointerUp(gestureId))
		assertFalse(router.complete(gestureId, ReaderPageGestureTerminalOutcome.CompletedTapAction))
	}

	@Test
	fun delayedNavigationTapKeepsOriginalIdWhileNewPointerBegins() {
		val router = ReaderPagePointerRouter(ReaderPageGestureLifecycle())
		val tapId = router.begin(20f, 20f, ReaderPageNewPointerDecision.Accept).gestureId
		assertEquals(ReaderPagePointerRoute.Content, router.pointerUp(tapId))

		val nextId = router.begin(40f, 40f, ReaderPageNewPointerDecision.Accept).gestureId
		assertTrue(nextId > tapId)
		assertTrue(
			router.completeDelayedTap(
				tapId,
				ReaderPageGestureTerminalOutcome.CommittedForward
			)
		)
		assertFalse(router.cancel(tapId, ReaderPageGestureTerminalOutcome.CancelledLifecycle))
		assertTrue(router.cancel(nextId, ReaderPageGestureTerminalOutcome.CancelledLifecycle))
	}

	@Test
	fun delayedTapPublishesResolvedNavigationOrChromeOutcome() {
		listOf(
			ReaderPageGestureTerminalOutcome.CommittedForward,
			ReaderPageGestureTerminalOutcome.CommittedBackward,
			ReaderPageGestureTerminalOutcome.CompletedTapAction
		).forEach { outcome ->
			val lifecycle = ReaderPageGestureLifecycle()
			val router = ReaderPagePointerRouter(lifecycle)
			val gestureId = router.begin(20f, 20f, ReaderPageNewPointerDecision.Accept).gestureId
			router.pointerUp(gestureId)

			assertTrue(router.completeDelayedTap(gestureId, outcome))
			assertEquals(outcome, lifecycle.terminalOutcome(gestureId))
		}
	}

	@Test
	fun lifecycleCancelBeforeTapConfirmationWinsExactlyOnce() {
		val router = ReaderPagePointerRouter(ReaderPageGestureLifecycle())
		val tapId = router.begin(20f, 20f, ReaderPageNewPointerDecision.Accept).gestureId
		router.pointerUp(tapId)

		assertTrue(router.cancel(tapId, ReaderPageGestureTerminalOutcome.CancelledLifecycle))
		assertFalse(
			router.completeDelayedTap(
				tapId,
				ReaderPageGestureTerminalOutcome.CompletedTapAction
			)
		)
	}

	@Test
	fun everyTerminalOutcomeIsCompareAndSetOnce() {
		ReaderPageGestureTerminalOutcome.values().forEach { outcome ->
			val lifecycle = ReaderPageGestureLifecycle()
			val gestureId = lifecycle.beginGesture()
			assertTrue(lifecycle.completeGesture(gestureId, outcome), outcome.name)
			assertFalse(
				lifecycle.completeGesture(
					gestureId,
					ReaderPageGestureTerminalOutcome.CancelledLifecycle
				),
				outcome.name
			)
		}
	}
}
