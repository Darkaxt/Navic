package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReaderPagePendingCallbackOwnersTest {
	@Test
	fun cancellationReleasesAndNotifiesEveryPendingOwner() {
		val retained = mutableListOf<Int>()
		val released = mutableListOf<Int>()
		val abandoned = mutableListOf<Int>()
		val owners = ReaderPagePendingCallbackOwners(
			retain = retained::add,
			release = released::add
		)
		owners.acquire(1) { abandoned += 1 }
		owners.acquire(2) { abandoned += 2 }

		owners.cancelAll()

		assertEquals(listOf(1, 2), retained)
		assertEquals(listOf(1, 2), released)
		assertEquals(listOf(1, 2), abandoned)
		assertEquals(0, owners.pendingCount())
	}

	@Test
	fun claimedOwnerSurvivesCloseUntilCallbackCompletes() {
		val released = mutableListOf<Int>()
		var abandoned = false
		val owners = ReaderPagePendingCallbackOwners<Int>(
			retain = {},
			release = released::add
		)
		val lease = requireNotNull(owners.acquire(7) { abandoned = true })
		val claimed = requireNotNull(owners.claim(lease))

		owners.close()

		assertEquals(0, owners.pendingCount())
		assertTrue(released.isEmpty())
		assertTrue(!abandoned)
		assertNull(owners.claim(lease))
		owners.complete(claimed)
		assertEquals(listOf(7), released)
	}

	@Test
	fun closeRejectsLaterOwnerWithoutRetainingIt() {
		val retained = mutableListOf<Int>()
		val released = mutableListOf<Int>()
		val owners = ReaderPagePendingCallbackOwners(
			retain = retained::add,
			release = released::add
		)
		owners.close()

		assertNull(owners.acquire(9) {})
		assertTrue(retained.isEmpty())
		assertTrue(released.isEmpty())
	}

	@Test
	fun repeatedFailureInstanceCannotAbortOwnerDrain() {
		val shared = IllegalStateException("shared")
		val releaseAttempts = mutableListOf<Int>()
		val cancellationAttempts = mutableListOf<Int>()
		val owners = ReaderPagePendingCallbackOwners<Int>(
			retain = {},
			release = { value ->
				releaseAttempts += value
				throw shared
			}
		)
		owners.acquire(1) {
			cancellationAttempts += 1
			throw shared
		}
		owners.acquire(2) {
			cancellationAttempts += 2
			throw shared
		}

		val failure = assertFailsWith<IllegalStateException> {
			owners.cancelAll()
		}

		assertSame(shared, failure)
		assertEquals(listOf(1, 2), releaseAttempts)
		assertEquals(listOf(1, 2), cancellationAttempts)
		assertTrue(failure.suppressed.isEmpty())
		assertEquals(0, owners.pendingCount())
	}
}
