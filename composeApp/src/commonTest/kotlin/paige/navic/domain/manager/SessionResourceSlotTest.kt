package paige.navic.domain.manager

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionResourceSlotTest {
	@Test
	fun operationsUseOneAtomicSnapshotAndLaterOperationsSeeSwap() = runBlocking {
		val slot = SessionResourceSlot("first")

		val first = slot.withResource { resource ->
			slot.swap("second")
			resource
		}
		val second = slot.withResource { it }

		assertEquals("first", first)
		assertEquals("second", second)
	}
}
