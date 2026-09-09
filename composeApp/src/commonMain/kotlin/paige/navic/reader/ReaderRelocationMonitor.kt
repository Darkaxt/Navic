package paige.navic.reader

/** Queue-owned synchronous, recursive monitor. Callbacks execute before the lock is released. */
internal expect class ReaderRelocationMonitor() {
	fun <T> withLock(block: () -> T): T
}
