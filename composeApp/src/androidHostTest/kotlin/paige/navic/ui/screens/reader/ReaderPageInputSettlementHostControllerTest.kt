package paige.navic.ui.screens.reader

import android.view.MotionEvent
import karacken.curl.GestureRejectionReason
import karacken.curl.PageChange
import karacken.curl.RenderFailureReason
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.ReaderPageGestureLifecycle
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPageLifecycleCancellationReason
import paige.navic.reader.ReaderPageNewPointerDecision
import paige.navic.reader.ReaderPagePointerRoute
import paige.navic.reader.ReaderPagePointerRouter
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationInputPolicy
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.readerPageOperationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ReaderPageInputSettlementHostControllerTest {
	private class FakeReaderPageHostCancellationPort : ReaderPageHostCancellationPort {
		val calls = mutableListOf<Pair<String, ReaderPageLifecycleCancellationReason>>()
		val pointerInterruptions = mutableListOf<Long>()
		val completedPointerOwnership = mutableListOf<Long>()
		var rendererAnimatorInFlight = false

		override fun cancelForPointerInterruption(gestureId: Long) {
			pointerInterruptions += gestureId
			rendererAnimatorInFlight = false
		}

		override fun clearCompletedPointerOwnership(gestureId: Long) {
			completedPointerOwnership += gestureId
		}

		override fun cancelActiveRendererGesture(reason: ReaderPageLifecycleCancellationReason) {
			calls += "renderer" to reason
			rendererAnimatorInFlight = false
		}

		override fun cancelReadableViewerDragPreview(reason: ReaderPageLifecycleCancellationReason) {
			calls += "drag-preview" to reason
		}

		override fun clearNativeTapState(reason: ReaderPageLifecycleCancellationReason) {
			calls += "tap" to reason
		}

		override fun clearSwipeTouchState(reason: ReaderPageLifecycleCancellationReason) {
			calls += "swipe" to reason
		}
	}

	private val ready = ReaderPageReadinessState(
		textureDeck = ReaderTextureDeckState.Ready,
		interaction = ReaderPageInteractionState.Ready
	)
	private val settling = ReaderPageReadinessState(
		textureDeck = ReaderTextureDeckState.Settling,
		interaction = ReaderPageInteractionState.Settling
	)
	private val nativeBinding = ReaderPresentationBinding(
		foliateSessionId = "fixture-session",
		publicationGeneration = 1L,
		viewportGeneration = 2L,
		profileGeneration = 3L,
		rasterGeneration = 4L,
		textureGeneration = 5L,
		preparationGeneration = 6L
	)

	private fun nativeTapContinuation(
		binding: ReaderPresentationBinding = nativeBinding,
		presentationToken: ReaderPresentationToken? = ReaderPresentationToken(7L),
		authorityPolicy: paige.navic.reader.ReaderPageOperationPolicy =
			readerPageOperationPolicy(ready),
		localSafetyPolicy: paige.navic.reader.ReaderPageOperationPolicy =
			readerPageOperationPolicy(ready)
	) = ReaderNativeTapContinuationIdentity(
		binding = binding,
		presentationToken = presentationToken,
		authorityPolicy = authorityPolicy,
		localSafetyPolicy = localSafetyPolicy
	)

	private fun host(
		lifecycle: ReaderPageGestureLifecycle = ReaderPageGestureLifecycle(),
		published: MutableList<Pair<Long, ReaderPageGestureTerminalOutcome>>? = null,
		cancellationPort: FakeReaderPageHostCancellationPort = FakeReaderPageHostCancellationPort(),
		readiness: ReaderPageReadinessState = ready,
		presentationInputPolicy: ReaderPresentationInputPolicy =
			ReaderPresentationInputPolicy.NativePage(readerPageOperationPolicy(readiness)),
		localReadiness: ReaderPageReadinessState = readiness,
		chromeToggleTarget: (Float, Float) -> Boolean = { _, _ -> false },
		onChromeToggle: () -> Unit = {},
		chromeTapTimeoutMillis: Long = 500L,
		publishLifecycleCancellation: (
			Long,
			ReaderPageLifecycleCancellationReason
		) -> Unit = { _, _ -> }
	): Triple<ReaderPageInputSettlementHostController, ReaderPagePointerRouter, FakeReaderPageHostCancellationPort> {
		val router = ReaderPagePointerRouter(lifecycle) { gestureId, outcome ->
			published?.add(gestureId to outcome)
		}
		val localSafetyPolicy = readerPageOperationPolicy(localReadiness)
		val continuationIdentity = (presentationInputPolicy as? ReaderPresentationInputPolicy.NativePage)
			?.takeIf {
				it.policy.newPointer == ReaderPageNewPointerDecision.Accept &&
					localSafetyPolicy.newPointer == ReaderPageNewPointerDecision.Accept
			}
			?.let {
				nativeTapContinuation(
					authorityPolicy = it.policy,
					localSafetyPolicy = localSafetyPolicy
				)
			}
		return Triple(
			ReaderPageInputSettlementHostController(
				initialPresentationInputPolicy = presentationInputPolicy,
				initialLocalSafetyPolicy = localSafetyPolicy,
				initialNativeTapContinuationIdentity = continuationIdentity,
				pointerRouter = router,
				cancellationPort = cancellationPort,
				chromeToggleTarget = chromeToggleTarget,
				onChromeToggle = onChromeToggle,
				chromeTapTimeoutMillis = chromeTapTimeoutMillis,
				publishLifecycleCancellation = publishLifecycleCancellation
			),
			router,
			cancellationPort
		)
	}

	@Test
	fun chromeOnlyTogglesExactlyOnceForAStationaryCenterTapWithoutPageRouting() {
		var toggles = 0
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val (host, router, _) = host(
			published = published,
			presentationInputPolicy = ReaderPresentationInputPolicy.ChromeOnly,
			localReadiness = settling,
			chromeToggleTarget = { x, y -> x in 40f..60f && y in 40f..60f },
			onChromeToggle = { toggles += 1 }
		)

		assertTrue(
			host.dispatchChromeOnlyPointer(
				ReaderPageHostPointerEvent.Down(50f, 50f, downTimeMillis = 100L)
			)
		)
		assertTrue(
			host.dispatchChromeOnlyPointer(
				ReaderPageHostPointerEvent.PositionedUp(
					x = 50f,
					y = 50f,
					touchSlop = 8f,
					eventTimeMillis = 140L
				)
			)
		)

		assertEquals(1, toggles)
		assertEquals(0, router.trackedSequenceCount())
		assertTrue(published.isEmpty())
	}

	@Test
	fun chromeOnlyRejectsEdgesDragLongPressMultiplePointersAndCancel() {
		var toggles = 0
		val (host, router, _) = host(
			presentationInputPolicy = ReaderPresentationInputPolicy.ChromeOnly,
			chromeToggleTarget = { x, y -> x in 40f..60f && y in 40f..60f },
			onChromeToggle = { toggles += 1 },
			chromeTapTimeoutMillis = 500L
		)

		fun tap(x: Float, y: Float, downTime: Long, upTime: Long = downTime + 40L) {
			assertTrue(
				host.dispatchChromeOnlyPointer(
					ReaderPageHostPointerEvent.Down(x, y, downTime)
				)
			)
			assertTrue(
				host.dispatchChromeOnlyPointer(
					ReaderPageHostPointerEvent.PositionedUp(x, y, 8f, upTime)
				)
			)
		}

		tap(10f, 50f, 100L)
		tap(90f, 50f, 200L)

		assertTrue(host.dispatchChromeOnlyPointer(ReaderPageHostPointerEvent.Down(50f, 50f, 300L)))
		assertTrue(host.dispatchChromeOnlyPointer(ReaderPageHostPointerEvent.Move(70f, 50f, 8f)))
		assertTrue(
			host.dispatchChromeOnlyPointer(
				ReaderPageHostPointerEvent.PositionedUp(70f, 50f, 8f, 340L)
			)
		)

		tap(50f, 50f, downTime = 400L, upTime = 901L)

		assertTrue(host.dispatchChromeOnlyPointer(ReaderPageHostPointerEvent.Down(50f, 50f, 1_000L)))
		assertTrue(host.dispatchChromeOnlyPointer(ReaderPageHostPointerEvent.SecondaryPointerDown))
		assertTrue(
			host.dispatchChromeOnlyPointer(
				ReaderPageHostPointerEvent.PositionedUp(50f, 50f, 8f, 1_040L)
			)
		)

		assertTrue(host.dispatchChromeOnlyPointer(ReaderPageHostPointerEvent.Down(50f, 50f, 1_100L)))
		assertTrue(host.dispatchChromeOnlyPointer(ReaderPageHostPointerEvent.Cancel))

		assertEquals(0, toggles)
		assertEquals(0, router.trackedSequenceCount())
	}

	@Test
	fun recoveryAndPolicyOrSafetyChangesCannotUpgradeOrCompleteAChromeTap() {
		var toggles = 0
		val (recovery, recoveryRouter, _) = host(
			presentationInputPolicy = ReaderPresentationInputPolicy.RecoveryOnly,
			chromeToggleTarget = { _, _ -> true },
			onChromeToggle = { toggles += 1 }
		)
		assertFalse(
			recovery.dispatchChromeOnlyPointer(
				ReaderPageHostPointerEvent.Down(50f, 50f, 100L)
			)
		)
		assertFalse(
			recovery.dispatchChromeOnlyPointer(
				ReaderPageHostPointerEvent.PositionedUp(50f, 50f, 8f, 140L)
			)
		)
		assertEquals(0, recoveryRouter.trackedSequenceCount())

		val (policyChanged, _, _) = host(
			presentationInputPolicy = ReaderPresentationInputPolicy.ChromeOnly,
			chromeToggleTarget = { _, _ -> true },
			onChromeToggle = { toggles += 1 }
		)
		assertTrue(
			policyChanged.dispatchChromeOnlyPointer(
				ReaderPageHostPointerEvent.Down(50f, 50f, 200L)
			)
		)
		policyChanged.updateInputPolicies(
			presentationInputPolicy = ReaderPresentationInputPolicy.RecoveryOnly,
			localSafetyPolicy = readerPageOperationPolicy(ready)
		)
		assertTrue(
			policyChanged.dispatchChromeOnlyPointer(
				ReaderPageHostPointerEvent.PositionedUp(50f, 50f, 8f, 240L)
			)
		)

		val (safetyChanged, _, _) = host(
			presentationInputPolicy = ReaderPresentationInputPolicy.ChromeOnly,
			localReadiness = ready,
			chromeToggleTarget = { _, _ -> true },
			onChromeToggle = { toggles += 1 }
		)
		assertTrue(
			safetyChanged.dispatchChromeOnlyPointer(
				ReaderPageHostPointerEvent.Down(50f, 50f, 300L)
			)
		)
		safetyChanged.updateInputPolicies(
			presentationInputPolicy = ReaderPresentationInputPolicy.ChromeOnly,
			localSafetyPolicy = readerPageOperationPolicy(settling)
		)
		assertTrue(
			safetyChanged.dispatchChromeOnlyPointer(
				ReaderPageHostPointerEvent.PositionedUp(50f, 50f, 8f, 340L)
			)
		)

		assertEquals(0, toggles)
	}

	@Test
	fun onlyNativePageAuthorityCanAdmitANewNativePointer() {
		val nonNativePolicies = listOf(
			ReaderPresentationInputPolicy.ShellCover,
			ReaderPresentationInputPolicy.ChromeOnly,
			ReaderPresentationInputPolicy.RecoveryOnly,
			ReaderPresentationInputPolicy.ClaimedCurl(ReaderPresentationToken(3L)),
			ReaderPresentationInputPolicy.LiveEngine
		)

		nonNativePolicies.forEachIndexed { index, policy ->
			val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
			val (host, _, _) = host(
				published = published,
				presentationInputPolicy = policy,
				localReadiness = ready
			)
			val down = host.dispatchPointer(
				ReaderPageHostPointerEvent.Down(10f, 10f, 100L + index)
			)
			val terminal = assertIs<ReaderPagePointerRoute.Terminal>(down.route)

			assertEquals(down.gestureId, terminal.gestureId)
			assertEquals(listOf(terminal.gestureId to terminal.outcome), published)
		}

		val (nativeHost, _, _) = host(
			presentationInputPolicy = ReaderPresentationInputPolicy.NativePage(
				readerPageOperationPolicy(ready)
			),
			localReadiness = ready
		)
		assertEquals(
			ReaderPagePointerRoute.Content,
			nativeHost.dispatchPointer(
				ReaderPageHostPointerEvent.Down(10f, 10f, 200L)
			).route
		)
	}

	@Test
	fun authorityRejectionCannotBeElevatedByLocalRendererReadiness() {
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val (host, _, _) = host(
			published = published,
			presentationInputPolicy = ReaderPresentationInputPolicy.NativePage(
				readerPageOperationPolicy(settling)
			),
			localReadiness = ready
		)

		val down = host.dispatchPointer(
			ReaderPageHostPointerEvent.Down(10f, 10f, 300L)
		)
		val terminal = assertIs<ReaderPagePointerRoute.Terminal>(down.route)
		assertEquals(ReaderPageGestureTerminalOutcome.RejectedSettling, terminal.outcome)
		assertEquals(listOf(terminal.gestureId to terminal.outcome), published)
	}

	@Test
	fun localRendererReadinessCanVetoButNeverCreateTheAuthorityGrant() {
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val (host, _, _) = host(
			published = published,
			presentationInputPolicy = ReaderPresentationInputPolicy.NativePage(
				readerPageOperationPolicy(ready)
			),
			localReadiness = settling
		)

		val down = host.dispatchPointer(
			ReaderPageHostPointerEvent.Down(10f, 10f, 400L)
		)
		val terminal = assertIs<ReaderPagePointerRoute.Terminal>(down.route)
		assertEquals(ReaderPageGestureTerminalOutcome.RejectedSettling, terminal.outcome)
		assertEquals(listOf(terminal.gestureId to terminal.outcome), published)
	}

	@Test
	fun claimedCurlRejectsANewPointerWithoutInterruptingTheOwnedStream() {
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val (host, _, _) = host(published = published)
		val gestureId = requireNotNull(
			host.dispatchPointer(
				ReaderPageHostPointerEvent.Down(100f, 200f, 500L)
			).gestureId
		)
		assertEquals(
			ReaderPagePointerRoute.ClaimCurl(gestureId),
			host.dispatchPointer(
				ReaderPageHostPointerEvent.Move(140f, 200f, 8f)
			).route
		)

		host.updateInputPolicies(
			presentationInputPolicy = ReaderPresentationInputPolicy.ClaimedCurl(
				ReaderPresentationToken(5L)
			),
			localSafetyPolicy = readerPageOperationPolicy(settling)
		)
		assertEquals(
			ReaderPagePointerRoute.Curl(gestureId),
			host.dispatchPointer(ReaderPageHostPointerEvent.Up).route
		)

		val nextDown = host.dispatchPointer(
			ReaderPageHostPointerEvent.Down(100f, 200f, 501L)
		)
		val terminal = assertIs<ReaderPagePointerRoute.Terminal>(nextDown.route)
		assertEquals(ReaderPageGestureTerminalOutcome.RejectedSettling, terminal.outcome)
		assertEquals(listOf(terminal.gestureId to terminal.outcome), published)
	}

	@Test
	fun equalDownTimesKeepDelayedTapAndActiveStreamDistinct() {
		val (host, _, _) = host()
		val sharedDownTime = 10L
		val firstId = requireNotNull(
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(10f, 10f, sharedDownTime)).gestureId
		)
		assertEquals(ReaderPagePointerRoute.Content, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
		val secondId = requireNotNull(
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(20f, 20f, sharedDownTime)).gestureId
		)

		assertTrue(secondId > firstId)
		assertEquals(2, host.contentGestureTokenCount())
		val firstTap = requireNotNull(host.takeDelayedTap(sharedDownTime))
		assertEquals(firstId, firstTap.gestureId)
		assertEquals(10f, firstTap.x)
		assertEquals(10f, firstTap.y)
		assertEquals(1, host.contentGestureTokenCount())
		assertTrue(
			host.completeDelayedTap(
				firstTap.gestureId,
				ReaderPageGestureTerminalOutcome.CompletedTapAction
			)
		)
		assertEquals(
			ReaderPageHostPointerDispatchResult(secondId, ReaderPagePointerRoute.Content),
			host.claimContentAction(sharedDownTime)
		)
		assertEquals(
			ReaderPagePointerRoute.Terminal(
				secondId,
				ReaderPageGestureTerminalOutcome.CompletedTapAction
			),
			host.dispatchPointer(ReaderPageHostPointerEvent.Cancel).route
		)
		assertEquals(0, host.contentGestureTokenCount())
		assertNull(host.takeDelayedTap(sharedDownTime))
	}

	@Test
	fun doubleTapCallbackOrderResolvesFirstBeforeSecondUp() {
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val (host, _, _) = host(published = published)
		val firstId = requireNotNull(
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(10f, 11f, 41L)).gestureId
		)
		assertEquals(ReaderPagePointerRoute.Content, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
		val secondId = requireNotNull(
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(90f, 91f, 42L)).gestureId
		)

		val firstTap = requireNotNull(host.takeOldestDelayedTap())
		assertEquals(firstId, firstTap.gestureId)
		assertEquals(10f, firstTap.x)
		assertEquals(11f, firstTap.y)
		assertTrue(host.completeDelayedTap(firstTap.gestureId, ReaderPageGestureTerminalOutcome.CompletedTapAction))
		assertNull(host.takeDelayedTap(41L))

		assertEquals(ReaderPagePointerRoute.Content, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
		val secondTap = requireNotNull(host.takeDelayedTap(42L))
		assertEquals(secondId, secondTap.gestureId)
		assertEquals(90f, secondTap.x)
		assertEquals(91f, secondTap.y)
		assertTrue(host.completeDelayedTap(secondTap.gestureId, ReaderPageGestureTerminalOutcome.CompletedTapAction))
		assertEquals(
			listOf(
				firstId to ReaderPageGestureTerminalOutcome.CompletedTapAction,
				secondId to ReaderPageGestureTerminalOutcome.CompletedTapAction
			),
			published
		)
		assertFalse(host.completeDelayedTap(firstId, ReaderPageGestureTerminalOutcome.CompletedTapAction))
		assertFalse(host.completeDelayedTap(secondId, ReaderPageGestureTerminalOutcome.CompletedTapAction))
		assertEquals(0, host.contentGestureTokenCount())
	}

	@Test
	fun secondaryPointerCancelsAcceptedStreamOnceAndConsumesUntilPrimaryEnd() {
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val (host, _, cancellationPort) = host(published = published)
		val gestureId = requireNotNull(
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(10f, 10f, 10L)).gestureId
		)

		assertEquals(
			ReaderPagePointerRoute.Terminal(gestureId, ReaderPageGestureTerminalOutcome.CancelledByUser),
			host.dispatchPointer(ReaderPageHostPointerEvent.SecondaryPointerDown).route
		)
		assertEquals(listOf(gestureId), cancellationPort.pointerInterruptions)
		assertEquals(0, host.contentGestureTokenCount())
		assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.SecondaryPointerDown).route)
		assertEquals(listOf(gestureId), cancellationPort.pointerInterruptions)
		assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.SecondaryPointerUp).route)
		assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.Move(12f, 10f, 8f)).route)
		assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
		assertEquals(ReaderPagePointerRoute.Ignore, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
		assertEquals(
			listOf(gestureId to ReaderPageGestureTerminalOutcome.CancelledByUser),
			published
		)
	}

	@Test
	fun rendererTerminalWhileDownConsumesTailUntilPhysicalEnd() {
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val lifecycle = ReaderPageGestureLifecycle()
		val (host, _, cancellationPort) = host(lifecycle = lifecycle, published = published)
		val gestureId = requireNotNull(
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(100f, 200f, 100L)).gestureId
		)
		assertEquals(
			ReaderPagePointerRoute.ClaimCurl(gestureId),
			host.dispatchPointer(ReaderPageHostPointerEvent.Move(120f, 200f, 8f)).route
		)

		assertTrue(host.complete(gestureId, ReaderPageGestureTerminalOutcome.FailedRenderer))
		assertEquals(listOf(gestureId), cancellationPort.completedPointerOwnership)
		assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.Move(140f, 200f, 8f)).route)
		assertTrue(host.onLifecycleEvent(ReaderPageHostLifecycleEvent.GlFailed).isEmpty())
		assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
		assertEquals(ReaderPagePointerRoute.Ignore, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
		assertEquals(
			listOf(gestureId to ReaderPageGestureTerminalOutcome.FailedRenderer),
			published
		)
		assertEquals(ReaderPageGestureTerminalOutcome.FailedRenderer, lifecycle.terminalOutcome(gestureId))
	}

	@Test
	fun cancelTerminatesAcceptedStreamOnceAndReplayIsIgnored() {
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val (host, _, cancellationPort) = host(published = published)
		val gestureId = requireNotNull(
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(20f, 20f, 20L)).gestureId
		)

		assertEquals(
			ReaderPagePointerRoute.Terminal(gestureId, ReaderPageGestureTerminalOutcome.CancelledByUser),
			host.dispatchPointer(ReaderPageHostPointerEvent.Cancel).route
		)
		assertEquals(listOf(gestureId), cancellationPort.pointerInterruptions)
		assertEquals(ReaderPagePointerRoute.Ignore, host.dispatchPointer(ReaderPageHostPointerEvent.Cancel).route)
		assertEquals(ReaderPagePointerRoute.Ignore, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
		assertEquals(
			listOf(gestureId to ReaderPageGestureTerminalOutcome.CancelledByUser),
			published
		)
	}

	@Test
	fun finalCancelClearsTombstoneBeforeNextPointerBegins() {
		val (host, _, _) = host()
		val firstId = requireNotNull(
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(20f, 20f, 20L)).gestureId
		)

		assertEquals(
			ReaderPagePointerRoute.Terminal(
				firstId,
				ReaderPageGestureTerminalOutcome.CancelledByUser
			),
			host.dispatchPointer(ReaderPageHostPointerEvent.Cancel).route
		)
		val second = host.dispatchPointer(
			ReaderPageHostPointerEvent.Down(30f, 30f, 30L)
		)
		assertTrue(requireNotNull(second.gestureId) > firstId)
		assertEquals(ReaderPagePointerRoute.Content, second.route)
	}

	@Test
	fun rejectedStreamConsumesSecondaryAndFinalActionsWithoutReplay() {
		listOf<ReaderPageHostPointerEvent>(
			ReaderPageHostPointerEvent.Up,
			ReaderPageHostPointerEvent.Cancel
		).forEach { finalEvent ->
			val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
			val (host, _, cancellationPort) = host(published = published, readiness = settling)
			val down = host.dispatchPointer(ReaderPageHostPointerEvent.Down(20f, 20f, 20L))
			val gestureId = requireNotNull(down.gestureId)
			assertEquals(
				ReaderPagePointerRoute.Terminal(
					gestureId,
					ReaderPageGestureTerminalOutcome.RejectedSettling
				),
				down.route
			)

			assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.SecondaryPointerDown).route)
			assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.SecondaryPointerUp).route)
			assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.Move(22f, 20f, 8f)).route)
			assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(finalEvent).route)
			assertEquals(ReaderPagePointerRoute.Ignore, host.dispatchPointer(finalEvent).route)
			assertTrue(cancellationPort.pointerInterruptions.isEmpty())
			assertEquals(
				listOf(gestureId to ReaderPageGestureTerminalOutcome.RejectedSettling),
				published
			)
		}
	}

	@Test
	fun positionedUpClaimsCurlWhenIntermediateMovesWereCoalesced() {
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val (host, router, _) = host(published = published)
		val gestureId = requireNotNull(
			host.dispatchPointer(
				ReaderPageHostPointerEvent.Down(900f, 400f, 100L)
			).gestureId
		)

		assertEquals(
			ReaderPagePointerRoute.ClaimCurl(gestureId),
			host.dispatchPointer(
				ReaderPageHostPointerEvent.PositionedUp(
					x = 100f,
					y = 400f,
					touchSlop = 8f
				)
			).route
		)
		assertEquals(0, host.contentGestureTokenCount())
		assertTrue(
			host.complete(
				gestureId,
				ReaderPageGestureTerminalOutcome.CancelledByUser
			)
		)
		assertEquals(
			listOf(gestureId to ReaderPageGestureTerminalOutcome.CancelledByUser),
			published
		)
		assertEquals(0, router.trackedSequenceCount())
	}

	@Test
	fun claimedPositionedUpMovesRendererToReleaseCoordinatesBeforeUp() {
		val event = MotionEvent.obtain(
			100L,
			140L,
			MotionEvent.ACTION_UP,
			100f,
			400f,
			0
		)
		val actions = mutableListOf<Int>()
		val coordinates = mutableListOf<Pair<Float, Float>>()

		try {
			assertEquals(
				ReaderPageCurlDispatchResult.Accepted,
				dispatchClaimedReaderPageCurlEvent(event) { dispatched ->
					actions += dispatched.actionMasked
					coordinates += dispatched.x to dispatched.y
					ReaderPageCurlDispatchResult.Accepted
				}
			)
			assertEquals(
				listOf(MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP),
				actions
			)
			assertEquals(listOf(100f to 400f, 100f to 400f), coordinates)
			assertEquals(MotionEvent.ACTION_UP, event.actionMasked)
		} finally {
			event.recycle()
		}
	}

	@Test
	fun syntheticPositionedUpMoveTerminalDoesNotReplayPhysicalUp() {
		val event = MotionEvent.obtain(
			100L,
			140L,
			MotionEvent.ACTION_UP,
			100f,
			400f,
			0
		)
		val actions = mutableListOf<Int>()

		try {
			assertEquals(
				ReaderPageCurlDispatchResult.TerminalPublished,
				dispatchClaimedReaderPageCurlEvent(event) { dispatched ->
					actions += dispatched.actionMasked
					ReaderPageCurlDispatchResult.TerminalPublished
				}
			)
			assertEquals(listOf(MotionEvent.ACTION_MOVE), actions)
		} finally {
			event.recycle()
		}
	}

	@Test
	fun productionHostLetsCommitAndSnapBackFinishWhileConcurrentDownIsRejectedOnce() {
		listOf(
			ReaderPageGestureTerminalOutcome.CommittedForward,
			ReaderPageGestureTerminalOutcome.CancelledByUser
		).forEach { settlementOutcome ->
			val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
			val (host, router, cancellationPort) = host(published = published)
			val primaryId = requireNotNull(
				host.dispatchPointer(ReaderPageHostPointerEvent.Down(100f, 200f, 100L)).gestureId
			)
			assertEquals(
				ReaderPagePointerRoute.ClaimCurl(primaryId),
				host.dispatchPointer(ReaderPageHostPointerEvent.Move(140f, 202f, 8f)).route
			)
			assertEquals(ReaderPagePointerRoute.Curl(primaryId), host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
			cancellationPort.rendererAnimatorInFlight = true

			host.updateInputPolicies(
				presentationInputPolicy = ReaderPresentationInputPolicy.NativePage(
					readerPageOperationPolicy(settling)
				),
				localSafetyPolicy = readerPageOperationPolicy(settling)
			)
			assertTrue(cancellationPort.rendererAnimatorInFlight)
			assertTrue(cancellationPort.calls.isEmpty())
			assertEquals(
				ReaderPageNewPointerDecision.Reject(
					ReaderPageGestureTerminalOutcome.RejectedSettling
				),
				readerPageOperationPolicy(settling).newPointer
			)

			val concurrentDown = host.dispatchPointer(ReaderPageHostPointerEvent.Down(120f, 200f, 120L))
			val concurrentId = requireNotNull(concurrentDown.gestureId)
			assertEquals(
				ReaderPagePointerRoute.Terminal(
					concurrentId,
					ReaderPageGestureTerminalOutcome.RejectedSettling
				),
				concurrentDown.route
			)
			assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
			assertTrue(host.complete(primaryId, settlementOutcome))
			assertFalse(host.complete(primaryId, ReaderPageGestureTerminalOutcome.CancelledLifecycle))
			assertEquals(
				listOf(
					concurrentId to ReaderPageGestureTerminalOutcome.RejectedSettling,
					primaryId to settlementOutcome
				),
				published
			)
			assertEquals(0, router.trackedSequenceCount())
			assertTrue(cancellationPort.calls.isEmpty())
		}
	}

	private val nonFinalLifecycleReasons = linkedMapOf(
		ReaderPageHostLifecycleEvent.CanvasDisabled to ReaderPageLifecycleCancellationReason.CanvasDisabled,
		ReaderPageHostLifecycleEvent.WindowHidden to ReaderPageLifecycleCancellationReason.CanvasDisabled,
		ReaderPageHostLifecycleEvent.ShellCoverShown to ReaderPageLifecycleCancellationReason.CanvasDisabled,
		ReaderPageHostLifecycleEvent.RendererReplaced to ReaderPageLifecycleCancellationReason.RendererReplaced,
		ReaderPageHostLifecycleEvent.ViewportChanged to ReaderPageLifecycleCancellationReason.RasterProfileInvalidated,
		ReaderPageHostLifecycleEvent.ReaderSettingsChanged to ReaderPageLifecycleCancellationReason.RasterProfileInvalidated,
		ReaderPageHostLifecycleEvent.ExternalRelocation to ReaderPageLifecycleCancellationReason.RasterProfileInvalidated,
		ReaderPageHostLifecycleEvent.RasterProfileInvalidated to ReaderPageLifecycleCancellationReason.RasterProfileInvalidated,
		ReaderPageHostLifecycleEvent.UnsafeContextLost to ReaderPageLifecycleCancellationReason.UnsafeContextLoss,
		ReaderPageHostLifecycleEvent.GlFailed to ReaderPageLifecycleCancellationReason.GlFailure
	)
	private val finalLifecycleReasons = linkedMapOf(
		ReaderPageHostLifecycleEvent.Detached to ReaderPageLifecycleCancellationReason.HostDetached,
		ReaderPageHostLifecycleEvent.Destroyed to ReaderPageLifecycleCancellationReason.HostDestroyed,
		ReaderPageHostLifecycleEvent.ReaderClosed to ReaderPageLifecycleCancellationReason.ReaderExit
	)
	private val allLifecycleReasons = finalLifecycleReasons + nonFinalLifecycleReasons
	private val physicalFinalEvents = listOf<ReaderPageHostPointerEvent>(
		ReaderPageHostPointerEvent.Up,
		ReaderPageHostPointerEvent.Cancel
	)

	@Test
	fun everyProductionLifecycleEventCarriesGestureIdAndMappedReason() {
		assertEquals(ReaderPageHostLifecycleEvent.values().toSet(), allLifecycleReasons.keys)
		allLifecycleReasons.forEach { (event, reason) ->
			val lifecycle = ReaderPageGestureLifecycle()
			val diagnostics = mutableListOf<Pair<Long, ReaderPageLifecycleCancellationReason>>()
			val (host, _, cancellationPort) = host(
				lifecycle = lifecycle,
				publishLifecycleCancellation = { gestureId, observedReason ->
					diagnostics += gestureId to observedReason
				}
			)
			val gestureId = requireNotNull(
				host.dispatchPointer(ReaderPageHostPointerEvent.Down(40f, 40f, 40L)).gestureId
			)

			assertEquals(listOf(gestureId), host.onLifecycleEvent(event))
			assertEquals(
				ReaderPageGestureTerminalOutcome.CancelledLifecycle,
				lifecycle.terminalOutcome(gestureId)
			)
			assertEquals(listOf(gestureId to reason), diagnostics)
			assertEquals(listOf("renderer", "drag-preview", "tap", "swipe"), cancellationPort.calls.map { it.first })
			assertTrue(cancellationPort.calls.all { it.second == reason })
		}
	}

	@Test
	fun acceptedStreamStaysConsumedAcrossEveryNonFinalLifecycleEvent() {
		nonFinalLifecycleReasons.forEach { (event, reason) ->
			physicalFinalEvents.forEach { finalEvent ->
				val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
				val (host, _, cancellationPort) = host(published = published)
				val gestureId = requireNotNull(
					host.dispatchPointer(ReaderPageHostPointerEvent.Down(70f, 70f, 70L)).gestureId
				)

				assertEquals(listOf(gestureId), host.onLifecycleEvent(event))
				assertEquals(0, host.contentGestureTokenCount())
				assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.SecondaryPointerUp).route)
				assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.Move(72f, 70f, 8f)).route)
				assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(finalEvent).route)
				assertEquals(ReaderPagePointerRoute.Ignore, host.dispatchPointer(finalEvent).route)
				assertEquals(
					listOf(gestureId to ReaderPageGestureTerminalOutcome.CancelledLifecycle),
					published
				)
				assertTrue(cancellationPort.calls.all { it.second == reason })
			}
		}
	}

	@Test
	fun rejectedStreamStaysConsumedAcrossEveryNonFinalLifecycleEvent() {
		nonFinalLifecycleReasons.forEach { (event, reason) ->
			physicalFinalEvents.forEach { finalEvent ->
				val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
				val (host, _, _) = host(published = published, readiness = settling)
				val rejectedDown = host.dispatchPointer(ReaderPageHostPointerEvent.Down(70f, 70f, 70L))
				val rejectedId = requireNotNull(rejectedDown.gestureId)

				assertTrue(host.onLifecycleEvent(event).isEmpty())
				assertFailsWith<IllegalArgumentException> { host.abandonPhysicalPointerStream(reason) }
				assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.SecondaryPointerUp).route)
				assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.Move(72f, 70f, 8f)).route)
				assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(finalEvent).route)
				assertEquals(ReaderPagePointerRoute.Ignore, host.dispatchPointer(finalEvent).route)
				assertEquals(
					listOf(rejectedId to ReaderPageGestureTerminalOutcome.RejectedSettling),
					published
				)
			}
		}
	}

	@Test
	fun finalLifecycleEventsRetainTombstoneUntilDeliveryCloses() {
		finalLifecycleReasons.forEach { (event, reason) ->
			val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
			val (host, _, cancellationPort) = host(published = published)
			val gestureId = requireNotNull(
				host.dispatchPointer(ReaderPageHostPointerEvent.Down(10f, 10f, 10L)).gestureId
			)

			assertFailsWith<IllegalStateException> { host.abandonPhysicalPointerStream(reason) }
			assertEquals(1, host.contentGestureTokenCount())
			assertEquals(ReaderPagePointerRoute.Content, host.dispatchPointer(ReaderPageHostPointerEvent.Move(12f, 10f, 8f)).route)
			assertEquals(listOf(gestureId), host.onLifecycleEvent(event))
			assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.Move(14f, 10f, 8f)).route)
			host.abandonPhysicalPointerStream(reason)
			assertEquals(ReaderPagePointerRoute.Ignore, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
			val postAbandonDown = host.dispatchPointer(ReaderPageHostPointerEvent.Down(20f, 20f, 20L))
			assertNull(postAbandonDown.gestureId)
			assertEquals(ReaderPagePointerRoute.Ignore, postAbandonDown.route)
			assertEquals(
				listOf(gestureId to ReaderPageGestureTerminalOutcome.CancelledLifecycle),
				published
			)
			assertTrue(cancellationPort.calls.all { it.second == reason })
		}
	}

	@Test
	fun finalLifecycleEventsAbandonRejectedStreamWithoutSecondTerminal() {
		finalLifecycleReasons.forEach { (event, reason) ->
			val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
			val (host, _, _) = host(published = published, readiness = settling)
			val gestureId = requireNotNull(
				host.dispatchPointer(ReaderPageHostPointerEvent.Down(30f, 30f, 30L)).gestureId
			)

			assertTrue(host.onLifecycleEvent(event).isEmpty())
			assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.Move(32f, 30f, 8f)).route)
			host.abandonPhysicalPointerStream(reason)
			assertEquals(ReaderPagePointerRoute.Ignore, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
			assertEquals(
				listOf(gestureId to ReaderPageGestureTerminalOutcome.RejectedSettling),
				published
			)
		}
	}

	@Test
	fun destroyedWhileAttachedConsumesPhysicalTailThroughPrimaryUp() {
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val (host, _, _) = host(published = published)
		val gestureId = requireNotNull(
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(10f, 10f, 10L)).gestureId
		)

		assertEquals(listOf(gestureId), host.onLifecycleEvent(ReaderPageHostLifecycleEvent.Destroyed))
		assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.Move(12f, 10f, 8f)).route)
		assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.SecondaryPointerUp).route)
		assertEquals(ReaderPagePointerRoute.Consume, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
		assertEquals(ReaderPagePointerRoute.Ignore, host.dispatchPointer(ReaderPageHostPointerEvent.Cancel).route)
		assertEquals(
			ReaderPageHostPointerDispatchResult(null, ReaderPagePointerRoute.Ignore),
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(20f, 20f, 20L))
		)
		assertEquals(
			listOf(gestureId to ReaderPageGestureTerminalOutcome.CancelledLifecycle),
			published
		)
	}

	@Test
	fun delayedNativeTapIsRevokedByEveryNonNativeAuthorityBeforeCallback() {
		val revokedPolicies = listOf(
			ReaderPresentationInputPolicy.ChromeOnly,
			ReaderPresentationInputPolicy.RecoveryOnly,
			ReaderPresentationInputPolicy.ShellCover,
			ReaderPresentationInputPolicy.ClaimedCurl(ReaderPresentationToken(9L)),
			ReaderPresentationInputPolicy.LiveEngine
		)

		revokedPolicies.forEachIndexed { index, policy ->
			val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
			val (host, _, _) = host(published = published)
			val downTime = 2_000L + index
			val gestureId = requireNotNull(
				host.dispatchPointer(
					ReaderPageHostPointerEvent.Down(20f, 30f, downTime)
				).gestureId
			)
			assertEquals(
				ReaderPagePointerRoute.Content,
				host.dispatchPointer(ReaderPageHostPointerEvent.Up).route
			)

			host.updateInputPolicies(
				presentationInputPolicy = policy,
				localSafetyPolicy = readerPageOperationPolicy(ready),
				nativeTapContinuationIdentity = null
			)

			assertNull(host.takeDelayedTap(downTime), "policy=$policy")
			assertEquals(0, host.contentGestureTokenCount())
			assertEquals(
				listOf(gestureId to ReaderPageGestureTerminalOutcome.CancelledLifecycle),
				published
			)
		}
	}

	@Test
	fun delayedNativeTapRequiresExactBindingTokenAndReadinessContinuity() {
		val changedIdentities = listOf<ReaderNativeTapContinuationIdentity?>(
			nativeTapContinuation(binding = nativeBinding.copy(viewportGeneration = 8L)),
			nativeTapContinuation(presentationToken = ReaderPresentationToken(8L)),
			nativeTapContinuation(
				authorityPolicy = readerPageOperationPolicy(settling)
			),
			nativeTapContinuation(
				localSafetyPolicy = readerPageOperationPolicy(settling)
			),
			null
		)

		changedIdentities.forEachIndexed { index, changedIdentity ->
			val (host, _, _) = host()
			val downTime = 3_000L + index
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(20f, 30f, downTime))
			host.dispatchPointer(ReaderPageHostPointerEvent.Up)

			host.updateInputPolicies(
				presentationInputPolicy = changedIdentity?.let {
					ReaderPresentationInputPolicy.NativePage(it.authorityPolicy)
				} ?: ReaderPresentationInputPolicy.RecoveryOnly,
				localSafetyPolicy = changedIdentity?.localSafetyPolicy
					?: readerPageOperationPolicy(ready),
				nativeTapContinuationIdentity = changedIdentity
			)

			assertNull(host.takeDelayedTap(downTime), "identity=$changedIdentity")
			assertEquals(0, host.contentGestureTokenCount())
		}
	}

	@Test
	fun unchangedExactNativeContinuityAllowsDelayedTapExactlyOnce() {
		val (host, _, _) = host()
		val downTime = 4_000L
		val gestureId = requireNotNull(
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(20f, 30f, downTime)).gestureId
		)
		host.dispatchPointer(ReaderPageHostPointerEvent.Up)
		val identity = nativeTapContinuation()

		host.updateInputPolicies(
			presentationInputPolicy = ReaderPresentationInputPolicy.NativePage(
				identity.authorityPolicy
			),
			localSafetyPolicy = identity.localSafetyPolicy,
			nativeTapContinuationIdentity = identity
		)

		assertEquals(gestureId, requireNotNull(host.takeDelayedTap(downTime)).gestureId)
		assertNull(host.takeDelayedTap(downTime))
	}

	@Test
	fun delayedTapIsClearedByDetachBackgroundDestroyAndViewerReplacement() {
		val events = listOf(
			ReaderPageHostLifecycleEvent.Detached,
			ReaderPageHostLifecycleEvent.WindowHidden,
			ReaderPageHostLifecycleEvent.Destroyed,
			ReaderPageHostLifecycleEvent.RendererReplaced
		)

		events.forEachIndexed { index, event ->
			val (host, _, _) = host()
			val downTime = 5_000L + index
			host.dispatchPointer(ReaderPageHostPointerEvent.Down(20f, 30f, downTime))
			host.dispatchPointer(ReaderPageHostPointerEvent.Up)

			host.onLifecycleEvent(event)

			assertNull(host.takeDelayedTap(downTime), "event=$event")
			assertEquals(0, host.contentGestureTokenCount())
		}
	}

	private data class TapTurnTerminalCase(
		val outcome: ReaderPageGestureTerminalOutcome,
		val detail: ReaderPageGestureTerminalDetail,
		val synchronous: Boolean
	)

	private data class TapTurnTerminalAttempt(
		val gestureId: Long,
		val outcome: ReaderPageGestureTerminalOutcome,
		val detail: ReaderPageGestureTerminalDetail,
		val won: Boolean
	)

	private inner class TapTurnHarness {
		val lifecycle = ReaderPageGestureLifecycle()
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val router = ReaderPagePointerRouter(lifecycle) { gestureId, outcome ->
			published += gestureId to outcome
		}
		val host = ReaderPageInputSettlementHostController(
			initialPresentationInputPolicy = ReaderPresentationInputPolicy.NativePage(
				readerPageOperationPolicy(ready)
			),
			initialLocalSafetyPolicy = readerPageOperationPolicy(ready),
			initialNativeTapContinuationIdentity = nativeTapContinuation(),
			pointerRouter = router,
			cancellationPort = FakeReaderPageHostCancellationPort()
		)
		val attempts = mutableListOf<TapTurnTerminalAttempt>()

		fun beginDelayedTap(downTimeMillis: Long): Long {
			val gestureId = requireNotNull(
				host.dispatchPointer(
					ReaderPageHostPointerEvent.Down(20f, 20f, downTimeMillis)
				).gestureId
			)
			assertEquals(ReaderPagePointerRoute.Content, host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
			assertEquals(gestureId, requireNotNull(host.takeDelayedTap(downTimeMillis)).gestureId)
			return gestureId
		}

		fun publishFromController(
			gestureId: Long,
			outcome: ReaderPageGestureTerminalOutcome,
			detail: ReaderPageGestureTerminalDetail
		): Boolean {
			val won = host.complete(gestureId, outcome)
			attempts += TapTurnTerminalAttempt(gestureId, outcome, detail, won)
			return won
		}
	}

	private class FakeTapTurnPort(
		private val terminalCase: TapTurnTerminalCase
	) : ReaderPageTapTurnPort {
		private var deferred: ((ReaderPageGestureTerminalOutcome, ReaderPageGestureTerminalDetail) -> Boolean)? = null
		private var retained: ((ReaderPageGestureTerminalOutcome, ReaderPageGestureTerminalDetail) -> Boolean)? = null
		var starts = 0
			private set

		override fun start(
			gestureId: Long,
			pageChange: PageChange,
			onTerminal: (ReaderPageGestureTerminalOutcome, ReaderPageGestureTerminalDetail) -> Boolean
		): ReaderPageTurnStartResult {
			starts += 1
			retained = onTerminal
			if (!terminalCase.synchronous) {
				check(deferred == null)
				deferred = onTerminal
				return ReaderPageTurnStartResult.Settling
			}
			check(onTerminal(terminalCase.outcome, terminalCase.detail))
			return ReaderPageTurnStartResult.TerminalPublished(
				outcome = terminalCase.outcome,
				detail = terminalCase.detail
			)
		}

		fun completeDeferred() {
			val callback = checkNotNull(deferred)
			deferred = null
			callback(terminalCase.outcome, terminalCase.detail)
		}

		fun replay(outcome: ReaderPageGestureTerminalOutcome) {
			checkNotNull(retained)(outcome, ReaderPageGestureTerminalDetail.ControllerCancelled)
		}
	}

	@Test
	fun synchronousTapTurnRejectionsPublishOriginalIdExactlyOnce() {
		listOf(
			TapTurnTerminalCase(
				ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable,
				ReaderPageGestureTerminalDetail.TapTurnUnavailable(PageChange.NEXT),
				synchronous = true
			),
			TapTurnTerminalCase(
				ReaderPageGestureTerminalOutcome.RejectedBoundary,
				ReaderPageGestureTerminalDetail.RendererRejected(11L, GestureRejectionReason.BOUNDARY),
				synchronous = true
			),
			TapTurnTerminalCase(
				ReaderPageGestureTerminalOutcome.RejectedSettling,
				ReaderPageGestureTerminalDetail.RendererRejected(12L, GestureRejectionReason.SETTLEMENT_RUNNING),
				synchronous = true
			),
			TapTurnTerminalCase(
				ReaderPageGestureTerminalOutcome.FailedRenderer,
				ReaderPageGestureTerminalDetail.TapTurnProtocolFailure(PageChange.NEXT),
				synchronous = true
			)
		).forEachIndexed { index, terminalCase ->
			val harness = TapTurnHarness()
			val port = FakeTapTurnPort(terminalCase)
			val controller = ReaderPageTapTurnControllerFacade(port, harness::publishFromController)
			val originalId = harness.beginDelayedTap(100L + index)

			val result = controller.turn(originalId, PageChange.NEXT)
				as ReaderPageTurnStartResult.TerminalPublished
			assertEquals(terminalCase.outcome, result.outcome)
			assertEquals(terminalCase.detail, result.detail)
			assertEquals(0, harness.host.contentGestureTokenCount())
			assertEquals(0, harness.router.trackedSequenceCount())
			assertEquals(listOf(originalId to terminalCase.outcome), harness.published)
			port.replay(ReaderPageGestureTerminalOutcome.CancelledLifecycle)
			assertEquals(1, harness.attempts.count { it.won })
			assertEquals(2, harness.attempts.size)
			assertEquals(terminalCase.outcome, harness.lifecycle.terminalOutcome(originalId))
		}
	}

	@Test
	fun overlappingTapTurnRejectsSecondGestureWithoutReplacingFirstTerminalSink() {
		val harness = TapTurnHarness()
		val port = FakeTapTurnPort(
			TapTurnTerminalCase(
				ReaderPageGestureTerminalOutcome.CommittedForward,
				ReaderPageGestureTerminalDetail.SettlementCompleted(PageChange.NEXT, 7),
				synchronous = false
			)
		)
		val controller = ReaderPageTapTurnControllerFacade(port, harness::publishFromController)
		val firstId = harness.beginDelayedTap(300L)
		val secondId = requireNotNull(
			harness.host.dispatchPointer(
				ReaderPageHostPointerEvent.Down(40f, 20f, 301L)
			).gestureId
		)

		assertEquals(ReaderPageTurnStartResult.Settling, controller.turn(firstId, PageChange.NEXT))
		assertEquals(ReaderPagePointerRoute.Content, harness.host.dispatchPointer(ReaderPageHostPointerEvent.Up).route)
		assertEquals(secondId, requireNotNull(harness.host.takeDelayedTap(301L)).gestureId)
		assertEquals(
			ReaderPageTurnStartResult.TerminalPublished(
				ReaderPageGestureTerminalOutcome.RejectedSettling,
				ReaderPageGestureTerminalDetail.TapTurnUnavailable(PageChange.NEXT)
			),
			controller.turn(secondId, PageChange.NEXT)
		)
		assertEquals(1, port.starts)
		assertEquals(
			listOf(secondId to ReaderPageGestureTerminalOutcome.RejectedSettling),
			harness.published
		)

		port.completeDeferred()
		assertEquals(
			listOf(
				secondId to ReaderPageGestureTerminalOutcome.RejectedSettling,
				firstId to ReaderPageGestureTerminalOutcome.CommittedForward
			),
			harness.published
		)
		assertEquals(1, harness.attempts.count { it.gestureId == firstId && it.won })
		assertEquals(1, harness.attempts.count { it.gestureId == secondId && it.won })
	}

	@Test
	fun deferredTapTurnTerminalsPublishOriginalIdExactlyOnce() {
		listOf(
			TapTurnTerminalCase(
				ReaderPageGestureTerminalOutcome.CommittedForward,
				ReaderPageGestureTerminalDetail.SettlementCompleted(PageChange.NEXT, 7),
				synchronous = false
			),
			TapTurnTerminalCase(
				ReaderPageGestureTerminalOutcome.CommittedBackward,
				ReaderPageGestureTerminalDetail.SettlementCompleted(PageChange.PREVIOUS, 6),
				synchronous = false
			),
			TapTurnTerminalCase(
				ReaderPageGestureTerminalOutcome.CancelledByUser,
				ReaderPageGestureTerminalDetail.SettlementCompleted(PageChange.NONE, 7),
				synchronous = false
			),
			TapTurnTerminalCase(
				ReaderPageGestureTerminalOutcome.CancelledByUser,
				ReaderPageGestureTerminalDetail.RendererCancelled(13L),
				synchronous = false
			),
			TapTurnTerminalCase(
				ReaderPageGestureTerminalOutcome.FailedRenderer,
				ReaderPageGestureTerminalDetail.RenderFailed(14L, RenderFailureReason.CONTEXT),
				synchronous = false
			)
		).forEachIndexed { index, terminalCase ->
			val harness = TapTurnHarness()
			val port = FakeTapTurnPort(terminalCase)
			val controller = ReaderPageTapTurnControllerFacade(port, harness::publishFromController)
			val originalId = harness.beginDelayedTap(200L + index)

			assertEquals(ReaderPageTurnStartResult.Settling, controller.turn(originalId, PageChange.NEXT))
			assertEquals(1, harness.router.trackedSequenceCount())
			port.completeDeferred()
			assertEquals(0, harness.host.contentGestureTokenCount())
			assertEquals(0, harness.router.trackedSequenceCount())
			assertEquals(listOf(originalId to terminalCase.outcome), harness.published)
			port.replay(ReaderPageGestureTerminalOutcome.FailedRecovery)
			assertEquals(1, harness.attempts.count { it.won })
			assertEquals(2, harness.attempts.size)
			assertEquals(terminalCase.outcome, harness.lifecycle.terminalOutcome(originalId))
		}
	}
}
