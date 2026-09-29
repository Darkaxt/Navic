package paige.navic.ui.screens.reader

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import paige.navic.reader.ReaderTransitionFailureReason
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

		assertEquals(1, owners.pendingCount())
		assertTrue(released.isEmpty())
		assertTrue(!abandoned)
		assertNull(owners.claim(lease))
		owners.complete(claimed)
		assertEquals(listOf(7), released)
		assertEquals(0, owners.pendingCount())
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

	@Test
	fun exactDrainConfirmsOnceWhenReleaseAndAbandonmentThrow() {
		val releaseFailure = IllegalStateException("bounded release failure")
		val abandonmentFailure = IllegalStateException("bounded abandonment failure")
		var releaseAttempts = 0
		var abandonmentAttempts = 0
		val owners = ReaderPagePendingCallbackOwners<Int>(
			retain = {},
			release = {
				releaseAttempts += 1
				throw releaseFailure
			}
		)
		owners.acquire(1) {
			abandonmentAttempts += 1
			throw abandonmentFailure
		}
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 53L,
			freezeToken = ReaderLegacyFreezeToken(54L)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			owners.freezeForTransitionActivation(domain)
		)
		val identity = owners.snapshotFrozenOwnership().single().physicalIdentity
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()

		val drain = runCatching {
			owners.drainFrozenOwnership(identity, confirmations::add)
		}

		assertNull(
			drain.exceptionOrNull(),
			"Accepted destructive drain cleanup must be total"
		)
		assertEquals(ReaderPortCommandResult.Accepted, drain.getOrThrow())
		assertEquals(1, releaseAttempts)
		assertEquals(1, abandonmentAttempts)
		assertEquals(listOf(identity), confirmations)
		assertTrue(owners.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			),
			owners.drainFrozenOwnership(identity, confirmations::add)
		)
		assertEquals(listOf(identity), confirmations)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			owners.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun claimedCallbackRemainsExactlyOwnedUntilItsRetainedValueReleaseReturns() {
		val retained = mutableListOf<Int>()
		val released = mutableListOf<Int>()
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 55L,
			freezeToken = ReaderLegacyFreezeToken(56L)
		)
		var callbackRows = emptyList<ReaderFrozenLegacyResource>()
		var releaseRows = emptyList<ReaderFrozenLegacyResource>()
		var drainResult: ReaderPortCommandResult? = null
		var confirmationsDuringCallback = emptyList<ReaderLegacyPhysicalIdentity>()
		var confirmationsDuringRelease = emptyList<ReaderLegacyPhysicalIdentity>()
		lateinit var owners: ReaderPagePendingCallbackOwners<Int>
		owners = ReaderPagePendingCallbackOwners(
			retain = retained::add,
			release = { value ->
				released += value
				releaseRows = owners.snapshotFrozenOwnership()
				confirmationsDuringRelease = confirmations.toList()
			}
		)
		val lease = requireNotNull(owners.acquire(7) {})

		fun dispatchClaimedCallback() {
			val claimed = requireNotNull(owners.claim(lease))
			try {
				assertEquals(
					ReaderPortCommandResult.Accepted,
					owners.freezeForTransitionActivation(domain)
				)
				callbackRows = owners.snapshotFrozenOwnership()
				callbackRows.singleOrNull()?.let { row ->
					drainResult = owners.drainFrozenOwnership(
						row.physicalIdentity,
						confirmations::add
					)
				}
				confirmationsDuringCallback = confirmations.toList()
			} finally {
				owners.complete(claimed)
			}
		}

		dispatchClaimedCallback()

		assertEquals(listOf(7), retained)
		assertEquals(1, callbackRows.size)
		val identity = callbackRows.single().physicalIdentity
		assertEquals(
			ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback,
			identity.source
		)
		assertEquals(ReaderPortCommandResult.Accepted, drainResult)
		assertTrue(confirmationsDuringCallback.isEmpty())
		assertEquals(listOf(7), released)
		assertEquals(listOf(identity), releaseRows.map { it.physicalIdentity })
		assertEquals(
			ReaderLegacyResourceState.ReleaseRequested,
			releaseRows.single().state
		)
		assertTrue(confirmationsDuringRelease.isEmpty())
		assertEquals(listOf(identity), confirmations)
		assertTrue(owners.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			),
			owners.drainFrozenOwnership(identity, confirmations::add)
		)
		assertEquals(listOf(identity), confirmations)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			owners.restoreAfterTransitionActivation(domain)
		)
	}
	@Test
	fun frozenCancelRetainsPendingLeaseAsReleasedUntilExactDrainConfirmation() {
		val released = mutableListOf<Int>()
		val abandoned = mutableListOf<Int>()
		val abandonmentEntered = CountDownLatch(1)
		val allowAbandonmentReturn = CountDownLatch(1)
		var rowsDuringRelease = emptyList<ReaderFrozenLegacyResource>()
		var rowsDuringAbandonment = emptyList<ReaderFrozenLegacyResource>()
		lateinit var owners: ReaderPagePendingCallbackOwners<Int>
		owners = ReaderPagePendingCallbackOwners(
			retain = {},
			release = { value ->
				released += value
				rowsDuringRelease = owners.snapshotFrozenOwnership()
			}
		)
		owners.acquire(9) {
			abandoned += 9
			rowsDuringAbandonment = owners.snapshotFrozenOwnership()
			abandonmentEntered.countDown()
			check(allowAbandonmentReturn.await(5, TimeUnit.SECONDS))
		}
		val domain = ReaderLegacyPhysicalDomain(
			readerSessionGeneration = 57L,
			freezeToken = ReaderLegacyFreezeToken(58L)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			owners.freezeForTransitionActivation(domain)
		)
		val identity = owners.snapshotFrozenOwnership().single().physicalIdentity
		var cancellationFailure: Throwable? = null
		val cancelling = Thread {
			try {
				owners.cancelAll()
			} catch (failure: Throwable) {
				cancellationFailure = failure
			}
		}
		cancelling.start()
		assertTrue(abandonmentEntered.await(5, TimeUnit.SECONDS))

		assertEquals(listOf(9), released)
		assertEquals(listOf(9), abandoned)
		assertEquals(listOf(identity), rowsDuringRelease.map { it.physicalIdentity })
		assertEquals(listOf(identity), rowsDuringAbandonment.map { it.physicalIdentity })
		assertTrue(cancelling.isAlive)
		allowAbandonmentReturn.countDown()
		cancelling.join(5_000L)
		assertTrue(!cancelling.isAlive)
		cancellationFailure?.let { throw it }

		val releasedRow = owners.snapshotFrozenOwnership().single()
		assertEquals(identity, releasedRow.physicalIdentity)
		assertEquals(ReaderLegacyResourceState.Released, releasedRow.state)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			owners.drainFrozenOwnership(identity, confirmations::add)
		)
		assertEquals(listOf(identity), confirmations)
		assertTrue(owners.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			owners.restoreAfterTransitionActivation(domain)
		)
	}
}
