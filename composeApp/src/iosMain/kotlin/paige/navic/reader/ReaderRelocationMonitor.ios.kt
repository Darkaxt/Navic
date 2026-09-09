@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package paige.navic.reader

import platform.Foundation.NSRecursiveLock

internal actual class ReaderRelocationMonitor actual constructor() {
	private val lock = NSRecursiveLock()

	actual fun <T> withLock(block: () -> T): T {
		lock.lock()
		try {
			return block()
		} finally {
			lock.unlock()
		}
	}
}
