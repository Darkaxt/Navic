package paige.navic.ui.screens.reader

import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration
import paige.navic.reader.ReaderPageGestureLifecycle
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPageLifecycleCancellationReason
import paige.navic.reader.ReaderPagePointerRouter
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPresentationInputPolicy
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.readerPageOperationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class KomikkuGestureDetectorWithLongTapBehaviorTest {
	private val readyPolicy = readerPageOperationPolicy(
		ReaderPageReadinessState(
			textureDeck = ReaderTextureDeckState.Ready,
			interaction = ReaderPageInteractionState.Ready
		)
	)

	@Test
	fun androidDoubleTapOrderResolvesFirstBeforeSecondUpExactlyOnce() {
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val lifecycle = ReaderPageGestureLifecycle()
		val router = ReaderPagePointerRouter(lifecycle) { id, outcome ->
			published += id to outcome
		}
		val host = ReaderPageInputSettlementHostController(
			initialPresentationInputPolicy = ReaderPresentationInputPolicy.NativePage(readyPolicy),
			initialLocalSafetyPolicy = readyPolicy,
			pointerRouter = router,
			cancellationPort = NoOpCancellationPort()
		)
		val resolved = mutableListOf<ReaderPageContentGestureToken>()
		val detector = KomikkuGestureDetectorWithLongTap(
			ApplicationProvider.getApplicationContext<Context>(),
			object : KomikkuGestureDetectorWithLongTap.Listener() {
				override fun onDown(event: MotionEvent): Boolean = true

				override fun onSingleTapConfirmed(event: MotionEvent): Boolean =
					host.takeDelayedTap(event.downTime)?.let(::complete) ?: false

				override fun onDoubleTap(event: MotionEvent): Boolean =
					host.takeOldestDelayedTap()?.let(::complete) ?: false

				override fun onDoubleTapEvent(event: MotionEvent): Boolean =
					if (event.actionMasked == MotionEvent.ACTION_UP) {
						host.takeDelayedTap(event.downTime)?.let(::complete) ?: false
					} else {
						true
					}

				private fun complete(tap: ReaderPageContentGestureToken): Boolean {
					resolved += tap
					return host.completeDelayedTap(
						tap.gestureId,
						ReaderPageGestureTerminalOutcome.CompletedTapAction
					)
				}
			}
		)

		fun send(
			downTime: Long,
			eventTime: Long,
			action: Int,
			x: Float,
			y: Float
		): Long? {
			val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
			return try {
				val id = when (action) {
					MotionEvent.ACTION_DOWN -> host.dispatchPointer(
						ReaderPageHostPointerEvent.Down(x, y, downTime)
					).gestureId
					MotionEvent.ACTION_UP -> {
						host.dispatchPointer(ReaderPageHostPointerEvent.Up)
						null
					}
					else -> error("Unsupported action: $action")
				}
				detector.onTouchEvent(event)
				id
			} finally {
				event.recycle()
			}
		}

		val firstId = requireNotNull(send(100L, 100L, MotionEvent.ACTION_DOWN, 10f, 11f))
		send(100L, 130L, MotionEvent.ACTION_UP, 10f, 11f)
		val secondId = requireNotNull(send(180L, 180L, MotionEvent.ACTION_DOWN, 20f, 21f))

		assertEquals(listOf(firstId), resolved.map { it.gestureId })
		assertEquals(10f, resolved.single().x)
		assertEquals(11f, resolved.single().y)

		send(180L, 210L, MotionEvent.ACTION_UP, 20f, 21f)

		assertEquals(listOf(firstId, secondId), resolved.map { it.gestureId })
		assertEquals(20f, resolved.last().x)
		assertEquals(21f, resolved.last().y)
		assertEquals(
			listOf(
				firstId to ReaderPageGestureTerminalOutcome.CompletedTapAction,
				secondId to ReaderPageGestureTerminalOutcome.CompletedTapAction
			),
			published
		)
		assertEquals(0, host.contentGestureTokenCount())
		assertEquals(0, router.trackedSequenceCount())
	}

	@Test
	fun rapidSpatiallyDistantTapsResolveSupersededIdentityBeforeSecondUp() {
		val published = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		val router = ReaderPagePointerRouter(ReaderPageGestureLifecycle()) { id, outcome ->
			published += id to outcome
		}
		val host = ReaderPageInputSettlementHostController(
			initialPresentationInputPolicy = ReaderPresentationInputPolicy.NativePage(readyPolicy),
			initialLocalSafetyPolicy = readyPolicy,
			pointerRouter = router,
			cancellationPort = NoOpCancellationPort()
		)
		val resolved = mutableListOf<ReaderPageContentGestureToken>()
		val detector = KomikkuGestureDetectorWithLongTap(
			ApplicationProvider.getApplicationContext<Context>(),
			object : KomikkuGestureDetectorWithLongTap.Listener() {
				override fun onDown(event: MotionEvent): Boolean = true

				override fun onSingleTapConfirmed(event: MotionEvent): Boolean =
					host.takeDelayedTap(event.downTime)?.let(::complete) ?: false

				override fun onSingleTapSuperseded(event: MotionEvent): Boolean =
					host.takeDelayedTap(event.downTime)?.let(::complete) ?: false

				private fun complete(tap: ReaderPageContentGestureToken): Boolean {
					resolved += tap
					return host.completeDelayedTap(
						tap.gestureId,
						ReaderPageGestureTerminalOutcome.CompletedTapAction
					)
				}
			}
		)

		fun send(
			downTime: Long,
			eventTime: Long,
			action: Int,
			x: Float,
			y: Float
		): Long? {
			val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
			return try {
				val id = when (action) {
					MotionEvent.ACTION_DOWN -> host.dispatchPointer(
						ReaderPageHostPointerEvent.Down(x, y, downTime)
					).gestureId
					MotionEvent.ACTION_UP -> {
						host.dispatchPointer(ReaderPageHostPointerEvent.Up)
						null
					}
					else -> error("Unsupported action: $action")
				}
				detector.onTouchEvent(event)
				id
			} finally {
				event.recycle()
			}
		}

		val firstId = requireNotNull(send(300L, 300L, MotionEvent.ACTION_DOWN, 10f, 10f))
		send(300L, 330L, MotionEvent.ACTION_UP, 10f, 10f)
		val secondId = requireNotNull(send(380L, 380L, MotionEvent.ACTION_DOWN, 1000f, 1000f))

		assertEquals(listOf(firstId), resolved.map { it.gestureId })
		assertEquals(10f, resolved.single().x)
		assertEquals(10f, resolved.single().y)

		send(380L, 410L, MotionEvent.ACTION_UP, 1000f, 1000f)
		Shadows.shadowOf(Looper.getMainLooper()).idleFor(
			Duration.ofMillis(ViewConfiguration.getDoubleTapTimeout().toLong() + 1L)
		)

		assertEquals(listOf(firstId, secondId), resolved.map { it.gestureId })
		assertEquals(
			listOf(
				firstId to ReaderPageGestureTerminalOutcome.CompletedTapAction,
				secondId to ReaderPageGestureTerminalOutcome.CompletedTapAction
			),
			published
		)
		assertEquals(0, host.contentGestureTokenCount())
		assertEquals(0, router.trackedSequenceCount())
	}

	@Test
	fun frameworkConfirmedTapIsNotConfirmedAgainByNextDistantDown() {
		var confirmations = 0
		val detector = KomikkuGestureDetectorWithLongTap(
			ApplicationProvider.getApplicationContext<Context>(),
			object : KomikkuGestureDetectorWithLongTap.Listener() {
				override fun onDown(event: MotionEvent): Boolean = true

				override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
					confirmations += 1
					return true
				}
			}
		)

		fun send(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
			val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
			try {
				detector.onTouchEvent(event)
			} finally {
				event.recycle()
			}
		}

		send(500L, 500L, MotionEvent.ACTION_DOWN, 10f, 10f)
		send(500L, 750L, MotionEvent.ACTION_UP, 10f, 10f)
		Shadows.shadowOf(Looper.getMainLooper()).idleFor(
			Duration.ofMillis(ViewConfiguration.getDoubleTapTimeout().toLong() + 1L)
		)
		assertEquals(1, confirmations)

		send(900L, 900L, MotionEvent.ACTION_DOWN, 1000f, 1000f)

		assertEquals(1, confirmations)
	}

	@Test
	fun confirmedLongTapIsNotEligibleForLaterTapSupersession() {
		var longTaps = 0
		var supersededTaps = 0
		val detector = KomikkuGestureDetectorWithLongTap(
			ApplicationProvider.getApplicationContext<Context>(),
			object : KomikkuGestureDetectorWithLongTap.Listener() {
				override fun onDown(event: MotionEvent): Boolean = true

				override fun onLongTapConfirmed(event: MotionEvent) {
					longTaps += 1
				}

				override fun onSingleTapSuperseded(event: MotionEvent): Boolean {
					supersededTaps += 1
					return true
				}
			}
		)

		fun send(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
			val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
			try {
				detector.onTouchEvent(event)
			} finally {
				event.recycle()
			}
		}

		val longPressTime = ViewConfiguration.getLongPressTimeout().toLong()
		send(1000L, 1000L, MotionEvent.ACTION_DOWN, 10f, 10f)
		Shadows.shadowOf(Looper.getMainLooper()).idleFor(
			Duration.ofMillis(longPressTime + 1L)
		)
		assertEquals(1, longTaps)
		send(1000L, 1000L + longPressTime + 1L, MotionEvent.ACTION_UP, 10f, 10f)
		send(
			1600L,
			1600L,
			MotionEvent.ACTION_DOWN,
			1000f,
			1000f
		)

		assertEquals(0, supersededTaps)
	}

	@Test
	fun lifecycleCancellationClearsQueuedTapConfirmation() {
		var confirmations = 0
		val detector = KomikkuGestureDetectorWithLongTap(
			ApplicationProvider.getApplicationContext<Context>(),
			object : KomikkuGestureDetectorWithLongTap.Listener() {
				override fun onDown(event: MotionEvent): Boolean = true

				override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
					confirmations += 1
					return true
				}
			}
		)

		fun send(downTime: Long, eventTime: Long, action: Int) {
			val event = MotionEvent.obtain(downTime, eventTime, action, 10f, 10f, 0)
			try {
				detector.onTouchEvent(event)
			} finally {
				event.recycle()
			}
		}

		send(2000L, 2000L, MotionEvent.ACTION_DOWN)
		send(2000L, 2030L, MotionEvent.ACTION_UP)
		detector.cancel()
		Shadows.shadowOf(Looper.getMainLooper()).idleFor(
			Duration.ofMillis(ViewConfiguration.getDoubleTapTimeout().toLong() + 1L)
		)

		assertEquals(0, confirmations)
	}

	private class NoOpCancellationPort : ReaderPageHostCancellationPort {
		override fun cancelForPointerInterruption(gestureId: Long) = Unit
		override fun clearCompletedPointerOwnership(gestureId: Long) = Unit
		override fun cancelActiveRendererGesture(reason: ReaderPageLifecycleCancellationReason) = Unit
		override fun cancelReadableViewerDragPreview(reason: ReaderPageLifecycleCancellationReason) = Unit
		override fun clearNativeTapState(reason: ReaderPageLifecycleCancellationReason) = Unit
		override fun clearSwipeTouchState(reason: ReaderPageLifecycleCancellationReason) = Unit
	}
}
