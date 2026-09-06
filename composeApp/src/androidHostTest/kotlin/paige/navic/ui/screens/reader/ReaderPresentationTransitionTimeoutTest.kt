package paige.navic.ui.screens.reader

import android.os.Looper
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import paige.navic.reader.*

@RunWith(RobolectricTestRunner::class)
class ReaderPresentationTransitionTimeoutTest {
	@Test
	fun bridgeTimesOutMissingCoverDrawAndRetryGetsOneFreshDeadline() {
		val fixture = Fixture()
		try {
			fixture.dispatch(ReaderPresentationEvent.ShellCoverRequested(2L))
			val token = assertIs<ReaderRequiredTransition.CommitShellCover>(fixture.decision.requiredTransition).token
			fixture.advance(6)
			repeat(3) { fixture.bridge.update(fixture.decision) }
			fixture.advance(4)
			assertEquals(ReaderPresentationFailureReason.TimedOut,
				assertIs<ReaderDiagnosticPresentation.Failure>(fixture.decision.diagnosticPresentation).reason)
			assertEquals(ReaderRequiredTransition.None, fixture.decision.requiredTransition)
			assertEquals(1, fixture.timeoutCount())
			fixture.dispatch(ReaderPresentationEvent.Retry)
			val retry = assertIs<ReaderRequiredTransition.CommitShellCover>(fixture.decision.requiredTransition)
			assertTrue(retry.token.value > token.value)
			fixture.dispatch(ReaderPresentationEvent.Retry)
			fixture.advance(9)
			assertEquals(1, fixture.timeoutCount())
			fixture.advance(1)
			assertEquals(2, fixture.timeoutCount())
			fixture.dispatch(ReaderPresentationEvent.Cancel)
			fixture.advance(30)
			assertEquals(2, fixture.timeoutCount())
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun visibilitySuspendsHostWorkAndResumesSamePendingAttempt() {
		val fixture = Fixture()
		try {
			fixture.dispatch(ReaderPresentationEvent.ShellCoverRequested(2L))
			val pending = fixture.state
			fixture.advance(4)
			fixture.dispatch(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost))
			assertEquals(ReaderRequiredTransition.None, fixture.decision.requiredTransition)
			fixture.advance(30)
			assertEquals(0, fixture.timeoutCount())
			fixture.dispatch(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityRestored))
			assertEquals(pending, fixture.state)
			fixture.advance(6)
			assertEquals(1, fixture.timeoutCount())
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun foliateWaitAndNativeWaitShareTheOriginalWholeTransactionDeadline() {
		val fixture = Fixture()
		try {
			val binding = fixture.binding
			fixture.dispatch(ReaderPresentationEvent.NativePagePresented(
				ReaderNativePagePresentationProof(binding, null, 1L, 100, 200, 4L, 5L)))
			val token = ReaderPresentationToken(7L)
			fixture.dispatch(ReaderPresentationEvent.CurlClaimed(
				ReaderCurlPresentationFrame(token, binding, 2L, 100, 200, 4L, 5L)))
			val next = binding.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture", 2L),
				textureGeneration = 8L)
			val ack = ReaderPageTurnSettlementAck("fixture-7", 2, "fixture", 4L, 8L)
			fixture.dispatch(ReaderPresentationEvent.CurlTerminal(token, binding, ack))
			fixture.advance(6)
			fixture.dispatch(ReaderPresentationEvent.FoliateRelocated(next, ack))
			fixture.advance(4)
			assertEquals(1, fixture.timeoutCount())
			assertEquals(ReaderPresentationFailureReason.TimedOut,
				assertIs<ReaderDiagnosticPresentation.Failure>(fixture.decision.diagnosticPresentation).reason)
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun terminalPublicationAndDisposalCancelScheduledDelivery() {
		for (closePublication in listOf(true, false)) {
			val fixture = Fixture()
			fixture.dispatch(ReaderPresentationEvent.NativePageRequested)
			if (closePublication) {
				fixture.dispatch(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.PublicationClosed))
			} else fixture.bridge.dispose()
			fixture.advance(30)
			assertEquals(0, fixture.timeoutCount())
			fixture.bridge.dispose()
		}
	}

	@Test
	fun existingHandbackTimerCannotExpireWhileHiddenAndRestoreKeepsWholeDeadline() {
		val fixture = Fixture()
		val token = ReaderPresentationToken(3L)
		fixture.state = fixture.state.copy(
			authority = ReaderPresentationAuthority.LiveEngineHandoffPending(
				retainedFrame = ReaderPresentationFrameOwner.LiveEngine(
					ReaderLiveEnginePresentationProof(ReaderPresentationToken(2L), fixture.binding, 1L)),
				token = token,
				binding = fixture.binding,
				direction = ReaderLiveEngineHandoffDirection.LiveEngineToNative
			)
		)
		val handler = android.os.Handler(Looper.getMainLooper())
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = object : ReaderNativePagePresentedFrameSource {
				override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long =
					error("No native candidate is available")
				override fun cancelPresentedFrameRequest(requestId: Long) = false
			},
			currentCandidate = { null },
			currentHandoffTransition = {
				fixture.decision.authoritativeLiveEngineToNativeTransitionOrNull()
			},
			handoffTimeoutScheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
				override fun postDelayed(action: Runnable, delayMillis: Long) = handler.postDelayed(action, delayMillis)
				override fun removeCallbacks(action: Runnable) = handler.removeCallbacks(action)
			}
		) { event ->
			fixture.events += event
			val result = readerPresentationReduce(fixture.state, event)
			fixture.state = result.state
			fixture.bridge.update(fixture.decision)
			readerTestPresentationReceipt(event, result.state, result.disposition)
		}
		try {
			fixture.bridge.update(fixture.decision)
			publisher.update()
			fixture.advance(4)
			fixture.dispatch(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost))
			publisher.update()
			fixture.advance(30)
			assertTrue(fixture.events.isEmpty(), "Neither deadline may fail hidden authority")
			fixture.dispatch(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityRestored))
			publisher.update()
			fixture.advance(5)
			assertTrue(fixture.events.isEmpty())
			fixture.advance(1)
			assertEquals(1, fixture.timeoutCount())
			publisher.update()
			fixture.advance(30)
			assertEquals(1, fixture.events.size, "The old handback clock must not deliver another terminal")
		} finally {
			publisher.dispose()
			fixture.bridge.dispose()
		}
	}

	@Test
	fun cancelledSchedulerCallbacksCannotExpireRestoredOrSuccessorAttempts() {
		val fixture = Fixture()
		val scheduled = mutableListOf<Pair<Runnable, Long>>()
		val removed = mutableListOf<Runnable>()
		var now = 0L
		val delivered = mutableListOf<ReaderPresentationEvent.TimedOut>()
		val timer = ReaderPresentationTransitionTimeout(
			scheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
				override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
					scheduled += action to delayMillis
					return true
				}
				override fun removeCallbacks(action: Runnable) { removed += action }
			}, nowMillis = { now }, onTimeout = { delivered += it; true })
		try {
			val pending = readerPresentationReduce(fixture.state, ReaderPresentationEvent.NativePageRequested)
			timer.update(pending.decision)
			val first = scheduled.single().first
			now = 4_000L
			val hidden = readerPresentationReduce(pending.state,
				ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost))
			timer.update(hidden.decision)
			first.run()
			assertTrue(delivered.isEmpty())
			now = 34_000L
			timer.update(pending.decision)
			assertEquals(listOf(10_000L, 6_000L), scheduled.map { it.second })
			val resumed = scheduled.last().first
			val failed = readerPresentationReduce(pending.state,
				ReaderPresentationEvent.TimedOut(pending.decision.pendingTransitionToken))
			val retry = readerPresentationReduce(failed.state, ReaderPresentationEvent.Retry)
			timer.update(retry.decision)
			resumed.run()
			assertTrue(delivered.isEmpty())
			assertEquals(10_000L, scheduled.last().second)
			val current = scheduled.last().first
			current.run()
			current.run()
			assertEquals(listOf(ReaderPresentationEvent.TimedOut(retry.decision.pendingTransitionToken)), delivered)
			assertEquals(listOf(first, resumed), removed)
		} finally { timer.cancel(); fixture.bridge.dispose() }
	}

	@Test
	fun rejectedSchedulingRetriesMissingReceiptWithoutReentrantOrDuplicateDelivery() {
		val fixture = Fixture()
		var decision = readerPresentationReduce(fixture.state, ReaderPresentationEvent.NativePageRequested).decision
		var attempts = 0
		var schedules = 0
		lateinit var timer: ReaderPresentationTransitionTimeout
		timer = ReaderPresentationTransitionTimeout(
			scheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
				override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
					schedules += 1
					return false
				}
				override fun removeCallbacks(action: Runnable) = Unit
			}, nowMillis = { 0L }, onTimeout = {
				attempts += 1
				timer.update(decision)
				attempts > 1
			})
		try {
			timer.update(decision)
			assertEquals(1, attempts)
			timer.update(decision)
			assertEquals(2, attempts)
			timer.update(decision)
			assertEquals(2, attempts)
			assertEquals(1, schedules)
			decision = decision.copy(lifecycle = ReaderPresentationLifecycleState.Destroyed)
			timer.update(decision)
		} finally { timer.cancel(); fixture.bridge.dispose() }
	}

	@Test
	fun bridgeInjectionRetriesRejectedSchedulingOnlyAfterMissingReceiptReturns() {
		var acceptReceipt = false
		var schedules = 0
		val fixture = Fixture(timeoutScheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
			override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
				schedules += 1
				return false
			}
			override fun removeCallbacks(action: Runnable) = Unit
		}, nowMillis = { 0L }, acceptTimeout = { acceptReceipt })
		try {
			fixture.dispatch(ReaderPresentationEvent.NativePageRequested)
			assertEquals(1, fixture.timeoutCount())
			assertEquals(ReaderDiagnosticPresentation.Hidden, fixture.decision.diagnosticPresentation)
			acceptReceipt = true
			fixture.bridge.update(fixture.decision)
			assertEquals(2, fixture.timeoutCount())
			assertEquals(ReaderPresentationFailureReason.TimedOut,
				assertIs<ReaderDiagnosticPresentation.Failure>(fixture.decision.diagnosticPresentation).reason)
			fixture.bridge.update(fixture.decision)
			assertEquals(2, fixture.timeoutCount())
			assertEquals(1, schedules)
		} finally { fixture.bridge.dispose() }
	}

	@Test
	fun bridgeInjectedCancellationAndDisposalFenceRetiredRunnables() {
		val scheduled = mutableListOf<Runnable>()
		val removed = mutableListOf<Runnable>()
		val fixture = Fixture(timeoutScheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
			override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
				scheduled += action
				assertEquals(10_000L, delayMillis)
				return true
			}
			override fun removeCallbacks(action: Runnable) { removed += action }
		}, nowMillis = { 0L })
		fixture.dispatch(ReaderPresentationEvent.NativePageRequested)
		fixture.dispatch(ReaderPresentationEvent.Cancel)
		scheduled.single().run()
		fixture.dispatch(ReaderPresentationEvent.NativePageRequested)
		fixture.bridge.dispose()
		scheduled.forEach(Runnable::run)
		assertEquals(scheduled, removed)
		assertEquals(0, fixture.timeoutCount())
	}

	private class Fixture(
		timeoutScheduler: ReaderPageRelocationDispatchTimeoutScheduler = HandlerTimeoutScheduler(),
		nowMillis: () -> Long = android.os.SystemClock::uptimeMillis,
		acceptTimeout: () -> Boolean = { true }
	) {
		val binding = ReaderPresentationBinding("fixture", 1L, 2L, 3L,
			ReaderDestinationCommitIdentity("fixture", 1L), 4L, 5L, 6L)
		var state = readerPresentationReduce(ReaderPresentationState(),
			ReaderPresentationEvent.PublicationOpened(binding)).state
		val decision get() = readerPresentationDecision(state)
		val events = mutableListOf<ReaderPresentationEvent>()
		private val host = object : ReaderPresentationCommitHost {
			override val isAttachedToWindow = true
			override val currentPresentationBinding get() = state.binding
			override var currentShellCoverGeneration: Long? = null
			override val shellCoverSelected get() = state.authority is ReaderPresentationAuthority.ShellCover
			override val measuredViewportWidth = 100
			override val measuredViewportHeight = 200
			override fun prepareOpaqueShellCover(coverGeneration: Long) { currentShellCoverGeneration = coverGeneration }
			override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
			override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
			override fun registerShellCoverDrawListener(onDraw: () -> Unit) = ReaderPresentationDrawRegistration {}
			override fun postShellCoverAnimationFrame(onFrame: () -> Unit) = Unit
		}
		val bridge = ReaderPresentationHostBridge(host,
			transitionTimeoutScheduler = timeoutScheduler, transitionNowMillis = nowMillis) { event ->
			events += event
			if (event is ReaderPresentationEvent.TimedOut && !acceptTimeout()) return@ReaderPresentationHostBridge null
			val reduction = readerPresentationReduce(state, event)
			state = reduction.state
			readerTestPresentationReceipt(event, state, reduction.disposition)
		}
		fun dispatch(event: ReaderPresentationEvent) {
			state = readerPresentationReduce(state, event).state
			bridge.update(decision)
		}
		fun advance(seconds: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds)) }
		fun timeoutCount() = events.count { it is ReaderPresentationEvent.TimedOut }
	}
}
