package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageRelocationRequest

internal fun readerPageRelocationDispatchRecoveryOrdinal(
	request: ReaderPageRelocationRequest,
	currentFoliateSessionId: String?,
	currentWebViewOrdinal: Int?
): Int = currentWebViewOrdinal
	?.takeIf { currentFoliateSessionId == request.foliateSessionId }
	?: request.sourceOrdinal

internal interface ReaderPageRelocationDispatchTimeoutScheduler {
	fun postDelayed(action: Runnable, delayMillis: Long): Boolean

	fun removeCallbacks(action: Runnable)
}

internal class ReaderPageRelocationDispatchTimeout(
	private val scheduler: ReaderPageRelocationDispatchTimeoutScheduler,
	private val timeoutMillis: Long = 10_000L,
	private val onTimeout: (ReaderPageRelocationRequest) -> Unit
) {
	private data class Active(
		val request: ReaderPageRelocationRequest,
		val action: Runnable
	)

	val pendingCallbackLimit: Int = 1

	private var active: Active? = null

	init {
		require(timeoutMillis > 0L)
	}

	fun arm(request: ReaderPageRelocationRequest) {
		check(active == null) { "A relocation dispatch timeout is already armed" }
		lateinit var action: Runnable
		action = Runnable {
			val current = active ?: return@Runnable
			if (current.action !== action || current.request != request) return@Runnable
			active = null
			onTimeout(request)
		}
		active = Active(request, action)
		if (!scheduler.postDelayed(action, timeoutMillis)) {
			active = null
			onTimeout(request)
		}
	}

	fun cancel(request: ReaderPageRelocationRequest): Boolean {
		val current = active?.takeIf { it.request == request } ?: return false
		active = null
		scheduler.removeCallbacks(current.action)
		return true
	}

	fun cancelAll() {
		val current = active ?: return
		active = null
		scheduler.removeCallbacks(current.action)
	}

	fun pendingCallbackCount(): Int = if (active == null) 0 else 1
}
