package paige.navic.ui.screens.reader

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class ReaderMainTerminalActionExecutor(
	val actionLimit: Int,
	scope: CoroutineScope,
	private val onActionFailure: (Throwable) -> Unit
) {
	private val pendingCount = AtomicInteger()
	private val closed = AtomicBoolean()
	private val actions = Channel<Runnable>(Channel.UNLIMITED)

	val drainJob: Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
		for (action in actions) {
			try {
				action.run()
			} catch (failure: Throwable) {
				try {
					onActionFailure(failure)
				} catch (_: Throwable) {
					// Failure reporting cannot terminate the sole drain owner.
				}
			} finally {
				check(pendingCount.decrementAndGet() >= 0) {
					"Main-terminal action ownership underflow"
				}
			}
		}
	}

	fun execute(action: Runnable): Boolean {
		if (closed.get()) return false
		val pending = pendingCount.incrementAndGet()
		if (pending > actionLimit) {
			pendingCount.decrementAndGet()
			return false
		}
		if (closed.get()) {
			pendingCount.decrementAndGet()
			return false
		}
		val sent = actions.trySend(action).isSuccess
		if (!sent) pendingCount.decrementAndGet()
		return sent
	}

	fun pendingActionCount(): Int = pendingCount.get()

	suspend fun closeAndJoin() {
		if (closed.compareAndSet(false, true)) actions.close()
		drainJob.join()
		check(pendingCount.get() == 0) {
			"Main-terminal actions did not drain"
		}
	}
}
