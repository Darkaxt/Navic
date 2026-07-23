package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPlayLikeCurlAdapterOwnerPoolTest {
	@Test
	fun ownerPoolRejectsAThirdAdapterUntilOneRetiringOwnerDrains() {
		val pool = ReaderPlayLikeCurlAdapterOwnerPool<String>(ownerLimit = 2)

		assertTrue(pool.tryAdd("profile-a"))
		assertTrue(pool.tryAdd("profile-b"))
		assertFalse(pool.tryAdd("profile-c"))
		assertEquals(listOf("profile-a", "profile-b"), pool.snapshot())
		assertEquals(2, pool.size)
		assertEquals(2, pool.ownerLimit)

		assertTrue(pool.remove("profile-a"))
		assertTrue(pool.tryAdd("profile-c"))
		assertEquals(listOf("profile-b", "profile-c"), pool.snapshot())
		assertEquals(2, pool.size)
	}
}
