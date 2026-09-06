package paige.navic.ui.screens.reader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import paige.navic.reader.ReaderDiagnosticPresentation
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationToken

/** One foreground-time deadline per common transaction, including its Foliate wait. */
internal class ReaderPresentationTransitionTimeout(
	private val scheduler: ReaderPageRelocationDispatchTimeoutScheduler = HandlerTimeoutScheduler(),
	private val nowMillis: () -> Long = SystemClock::uptimeMillis,
	private val timeoutMillis: Long = 10_000L,
	private val onTimeout: (ReaderPresentationEvent.TimedOut) -> Boolean
) {
	private class Pending(val token: ReaderPresentationToken, var remaining: Long) {
		var startedAt = 0L
		var action: Runnable? = null
		var delivered = false
	}

	private var pending: Pending? = null

	init { require(timeoutMillis > 0L) }

	fun update(decision: ReaderPresentationDecision) {
		val token = decision.pendingTransitionToken
		if (token == null || decision.diagnosticPresentation is ReaderDiagnosticPresentation.Failure ||
			decision.lifecycle == ReaderPresentationLifecycleState.Destroyed
		) {
			cancel()
			return
		}
		if (pending?.token != token) {
			cancel()
			pending = Pending(token, timeoutMillis)
		}
		val attempt = checkNotNull(pending)
		if (decision.lifecycle != ReaderPresentationLifecycleState.Foreground) {
			pause(attempt)
			return
		}
		if (attempt.action != null || attempt.delivered) return
		if (attempt.remaining == 0L) {
			deliver(attempt)
			return
		}
		lateinit var action: Runnable
		action = Runnable {
			if (pending !== attempt || attempt.action !== action) return@Runnable
			attempt.action = null
			attempt.remaining = 0L
			deliver(attempt)
		}
		attempt.startedAt = nowMillis()
		attempt.action = action
		if (!scheduler.postDelayed(action, attempt.remaining)) action.run()
	}

	private fun deliver(attempt: Pending) {
		attempt.delivered = true
		val accepted = onTimeout(ReaderPresentationEvent.TimedOut(attempt.token))
		// A missing common receipt can retry on the next host/authority update.
		if (pending === attempt && !accepted) attempt.delivered = false
	}

	private fun pause(attempt: Pending) {
		val action = attempt.action ?: return
		attempt.action = null
		attempt.remaining = (attempt.remaining - (nowMillis() - attempt.startedAt).coerceAtLeast(0L))
			.coerceAtLeast(0L)
		scheduler.removeCallbacks(action)
	}

	fun cancel() {
		val attempt = pending ?: return
		pending = null
		attempt.action?.let(scheduler::removeCallbacks)
	}
}

internal class HandlerTimeoutScheduler : ReaderPageRelocationDispatchTimeoutScheduler {
	private val handler = Handler(Looper.getMainLooper())
	override fun postDelayed(action: Runnable, delayMillis: Long): Boolean =
		handler.postDelayed(action, delayMillis)
	override fun removeCallbacks(action: Runnable) = handler.removeCallbacks(action)
}
