package paige.navic.shared

import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

internal class FutureConnectionOwner<T : Any>(
	private val onConnected: (T) -> Unit,
	private val onConnectionFailed: (Throwable) -> Unit,
	private val onDisconnected: (T) -> Unit,
	private val releaseFuture: (ListenableFuture<T>) -> Unit
) : AutoCloseable {
	private val lock = Any()
	private var activeFuture: ListenableFuture<T>? = null
	private var activeValue: T? = null

	val connectedValue: T?
		get() = synchronized(lock) { activeValue }

	fun connect(future: ListenableFuture<T>): Boolean {
		synchronized(lock) {
			if (activeFuture != null) return false
			activeFuture = future
		}

		Futures.addCallback(
			future,
			object : FutureCallback<T> {
				override fun onSuccess(result: T?) {
					if (result == null) {
						handleFailure(future, IllegalStateException("Connection completed without a value"))
						return
					}
					val accepted = synchronized(lock) {
						if (activeFuture !== future) {
							false
						} else {
							activeValue = result
							true
						}
					}
					if (accepted) onConnected(result)
				}

				override fun onFailure(error: Throwable) {
					handleFailure(future, error)
				}
			},
			MoreExecutors.directExecutor()
		)
		return true
	}

	fun disconnect(value: T): Boolean {
		val future = synchronized(lock) {
			if (activeValue !== value) return false
			activeValue = null
			activeFuture.also { activeFuture = null }
		} ?: return false

		releaseFuture(future)
		onDisconnected(value)
		return true
	}

	override fun close() {
		val future = synchronized(lock) {
			activeValue = null
			activeFuture.also { activeFuture = null }
		}
		future?.let(releaseFuture)
	}

	private fun handleFailure(future: ListenableFuture<T>, error: Throwable) {
		val accepted = synchronized(lock) {
			if (activeFuture !== future) {
				false
			} else {
				activeValue = null
				activeFuture = null
				true
			}
		}
		if (!accepted) return
		releaseFuture(future)
		onConnectionFailed(error)
	}
}
