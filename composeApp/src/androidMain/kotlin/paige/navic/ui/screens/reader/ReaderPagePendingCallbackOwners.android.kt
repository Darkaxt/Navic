package paige.navic.ui.screens.reader

internal class ReaderPagePendingCallbackOwners<T : Any>(
	private val retain: (T) -> Unit,
	private val release: (T) -> Unit
) {
	internal class Lease<T : Any> internal constructor(
		internal val value: T,
		internal val onAbandoned: () -> Unit
	) {
		internal var released = false
	}

	private val lock = Any()
	private val pending = linkedSetOf<Lease<T>>()
	private var closed = false

	fun acquire(value: T, onAbandoned: () -> Unit): Lease<T>? = synchronized(lock) {
		if (closed) return@synchronized null
		retain(value)
		Lease(value, onAbandoned).also(pending::add)
	}

	fun claim(lease: Lease<T>): Lease<T>? = synchronized(lock) {
		if (pending.remove(lease)) lease else null
	}

	fun complete(lease: Lease<T>) {
		check(!lease.released) { "Pending callback owner was released twice" }
		lease.released = true
		release(lease.value)
	}

	fun abandon(lease: Lease<T>) {
		var failure: Throwable? = null
		try {
			complete(lease)
		} catch (next: Throwable) {
			failure = next
		}
		try {
			lease.onAbandoned()
		} catch (next: Throwable) {
			val first = failure
			if (first == null) failure = next
			else if (next !== first) first.addSuppressed(next)
		}
		failure?.let { throw it }
	}

	fun cancelAll() {
		drain(close = false)
	}

	fun close() {
		drain(close = true)
	}

	fun pendingCount(): Int = synchronized(lock) { pending.size }

	private fun drain(close: Boolean) {
		val leases = synchronized(lock) {
			if (close) closed = true
			pending.toList().also { pending.clear() }
		}
		var failure: Throwable? = null
		leases.forEach { lease ->
			try {
				abandon(lease)
			} catch (next: Throwable) {
				val first = failure
				if (first == null) failure = next
				else if (next !== first) first.addSuppressed(next)
			}
		}
		failure?.let { throw it }
	}
}
