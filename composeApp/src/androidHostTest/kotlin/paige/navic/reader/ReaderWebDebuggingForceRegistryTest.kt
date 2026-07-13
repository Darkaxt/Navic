package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderWebDebuggingForceRegistryTest {
	@Test
	fun disabledLeaseNeverForcesWebContentsDebugging() {
		val transitions = mutableListOf<Boolean>()
		val registry = ReaderWebDebuggingForceRegistry(transitions::add)

		registry.acquire(enabled = false).close()

		assertFalse(registry.isForced())
		assertEquals(emptyList(), transitions)
	}

	@Test
	fun forcedDebuggingRemainsUntilLastEnabledLeaseCloses() {
		val transitions = mutableListOf<Boolean>()
		val registry = ReaderWebDebuggingForceRegistry(transitions::add)
		val first = registry.acquire(enabled = true)
		val second = registry.acquire(enabled = true)

		first.close()
		assertTrue(registry.isForced())
		second.close()
		second.close()

		assertFalse(registry.isForced())
		assertEquals(listOf(true, false), transitions)
	}

	@Test
	fun forceCanBeAcquiredAgainAfterEveryOwnerReleases() {
		val transitions = mutableListOf<Boolean>()
		val registry = ReaderWebDebuggingForceRegistry(transitions::add)

		registry.acquire(enabled = true).close()
		registry.acquire(enabled = true).close()

		assertFalse(registry.isForced())
		assertEquals(listOf(true, false, true, false), transitions)
	}
}
