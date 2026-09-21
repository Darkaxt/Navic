package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import paige.navic.reader.ReaderTransitionResourceKind

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

	@Test
	fun freezeFencesAcquisitionAndExactDrainAbandonsOnlyTheSelectedLease() {
		val released = mutableListOf<Int>()
		val abandoned = mutableListOf<Int>()
		val owners = ReaderPagePendingCallbackOwners<Int>(
			retain = {},
			release = released::add
		)
		owners.acquire(1) { abandoned += 1 }
		owners.acquire(2) { abandoned += 2 }
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 51L,
			freezeToken = ReaderLegacyFreezeToken(52L)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			owners.freezeForTransitionActivation(domain)
		)
		val rows = owners.snapshotFrozenOwnership()

		assertEquals(2, rows.size)
		assertTrue(rows.all { row ->
			row.physicalIdentity.domain == domain &&
				row.physicalIdentity.source ==
				ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback &&
				row.kind == ReaderTransitionResourceKind.CallbackRegistration
		})
		assertNull(owners.acquire(3) { })
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			owners.drainFrozenOwnership(rows.first().physicalIdentity, confirmations::add)
		)
		assertEquals(listOf(1), released)
		assertEquals(listOf(1), abandoned)
		assertEquals(listOf(rows.first().physicalIdentity), confirmations)
		assertEquals(listOf(rows.last()), owners.snapshotFrozenOwnership())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			owners.drainFrozenOwnership(rows.last().physicalIdentity, confirmations::add)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			owners.restoreAfterTransitionActivation(domain)
		)
		assertTrue(owners.acquire(4) { } != null)
		owners.close()
	}
}
