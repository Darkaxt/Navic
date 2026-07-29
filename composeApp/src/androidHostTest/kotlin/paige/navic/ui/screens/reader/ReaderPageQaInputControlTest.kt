package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPageQaInputControlTest {
	@Test
	fun admittedInputConsumesItsExactArmedIdentityOnce() {
		ReaderPageQaInputControl.consume()
		assertTrue(ReaderPageQaInputControl.arm("input-1"))
		assertFalse(ReaderPageQaInputControl.arm("input-2"))
		assertEquals("input-1", ReaderPageQaInputControl.consume())
		assertNull(ReaderPageQaInputControl.consume())
	}

	@Test
	fun silentInputCanClearOnlyItsOwnStillPendingIdentity() {
		ReaderPageQaInputControl.consume()
		assertTrue(ReaderPageQaInputControl.arm("input-3"))
		assertFalse(ReaderPageQaInputControl.clear("input-4"))
		assertTrue(ReaderPageQaInputControl.clear("input-3"))
		assertNull(ReaderPageQaInputControl.consume())
	}
}
