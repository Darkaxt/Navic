package paige.navic.reader

internal actual class ReaderRelocationMonitor actual constructor() {
	private val lock = Any()

	actual fun <T> withLock(block: () -> T): T = synchronized(lock) { block() }
}
