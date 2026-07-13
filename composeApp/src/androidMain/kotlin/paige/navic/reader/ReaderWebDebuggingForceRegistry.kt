package paige.navic.reader

import java.util.concurrent.atomic.AtomicBoolean

internal class ReaderWebDebuggingForceRegistry(
	private val onForcedStateChanged: (Boolean) -> Unit
) {
	private val lock = Any()
	private var ownerCount = 0

	fun acquire(enabled: Boolean): AutoCloseable {
		if (!enabled) return AutoCloseable {}

		synchronized(lock) {
			ownerCount += 1
			if (ownerCount == 1) onForcedStateChanged(true)
		}

		val released = AtomicBoolean(false)
		return AutoCloseable {
			if (!released.compareAndSet(false, true)) return@AutoCloseable
			synchronized(lock) {
				check(ownerCount > 0)
				ownerCount -= 1
				if (ownerCount == 0) onForcedStateChanged(false)
			}
		}
	}

	fun isForced(): Boolean = synchronized(lock) { ownerCount > 0 }
}
