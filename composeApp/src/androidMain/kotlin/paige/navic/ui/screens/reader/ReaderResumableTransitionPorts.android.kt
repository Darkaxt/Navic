package paige.navic.ui.screens.reader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionFact

internal enum class ReaderTransitionMode { Shadow, Active }

internal fun interface ReaderTransitionClockRegistration {
	fun cancel()
}

internal interface ReaderTransitionClock {
	fun nowMillis(): Long
	fun schedule(atMillis: Long, action: () -> Unit): ReaderTransitionClockRegistration?
}

internal interface ReaderResumableTransitionPorts {
	val clock: ReaderTransitionClock

	fun issue(
		command: ReaderTransitionCommand,
		onFact: (ReaderTransitionFact) -> Unit
	)
}

internal class AndroidReaderTransitionClock(
	private val handler: Handler = Handler(Looper.getMainLooper())
) : ReaderTransitionClock {
	override fun nowMillis(): Long = SystemClock.uptimeMillis()

	override fun schedule(
		atMillis: Long,
		action: () -> Unit
	): ReaderTransitionClockRegistration? {
		val runnable = Runnable(action)
		val accepted = handler.postAtTime(runnable, atMillis)
		return if (accepted) {
			ReaderTransitionClockRegistration { handler.removeCallbacks(runnable) }
		} else {
			null
		}
	}
}

internal class ReaderShadowTransitionPorts(
	override val clock: ReaderTransitionClock = AndroidReaderTransitionClock()
) : ReaderResumableTransitionPorts {
	override fun issue(
		command: ReaderTransitionCommand,
		onFact: (ReaderTransitionFact) -> Unit
	) {
		error("Shadow transition ports cannot issue mutating commands")
	}
}
