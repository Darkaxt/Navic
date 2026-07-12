package paige.navic.shared

import com.google.common.util.concurrent.SettableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutureConnectionOwnerTest {
	@Test
	fun failedConnectionIsReleasedAndCanReconnect() {
		val failures = mutableListOf<Throwable>()
		val released = mutableListOf<SettableFuture<String>>()
		val owner = FutureConnectionOwner<String>(
			onConnected = {},
			onConnectionFailed = failures::add,
			onDisconnected = {},
			releaseFuture = { released += it as SettableFuture<String> }
		)
		val failed = SettableFuture.create<String>()

		assertTrue(owner.connect(failed))
		failed.setException(IllegalStateException("service unavailable"))

		assertEquals(1, failures.size)
		assertEquals(listOf(failed), released)
		assertNull(owner.connectedValue)
		assertTrue(owner.connect(SettableFuture.create()))
	}

	@Test
	fun closeReleasesSynchronouslyAndIgnoresStaleCompletion() {
		val connected = mutableListOf<String>()
		val released = mutableListOf<SettableFuture<String>>()
		val owner = FutureConnectionOwner<String>(
			onConnected = connected::add,
			onConnectionFailed = {},
			onDisconnected = {},
			releaseFuture = { released += it as SettableFuture<String> }
		)
		val future = SettableFuture.create<String>()
		owner.connect(future)

		owner.close()
		future.set("late controller")

		assertEquals(listOf(future), released)
		assertTrue(connected.isEmpty())
		assertNull(owner.connectedValue)
	}

	@Test
	fun disconnectOnlyClearsTheActiveController() {
		val disconnected = mutableListOf<String>()
		val released = mutableListOf<SettableFuture<String>>()
		val owner = FutureConnectionOwner<String>(
			onConnected = {},
			onConnectionFailed = {},
			onDisconnected = disconnected::add,
			releaseFuture = { released += it as SettableFuture<String> }
		)
		val future = SettableFuture.create<String>()
		owner.connect(future)
		future.set("active")

		assertFalse(owner.disconnect("other"))
		assertTrue(owner.disconnect(owner.connectedValue!!))

		assertEquals(listOf("active"), disconnected)
		assertEquals(listOf(future), released)
		assertNull(owner.connectedValue)
	}
}
