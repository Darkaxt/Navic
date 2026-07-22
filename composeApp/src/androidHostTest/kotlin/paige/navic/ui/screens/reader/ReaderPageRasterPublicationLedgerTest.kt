package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPageRasterPublicationLedgerTest {
	@Test
	fun invalidationRejectsQueuedWorkAndReleasesItsStagedValue() {
		val released = mutableListOf<String>()
		val callbacks = mutableListOf<Boolean>()
		val ledger = ReaderPageRasterPublicationLedger<String>(released::add)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "queued", callbacks::add)
		)

		ledger.invalidate()

		assertNull(ledger.acquireForPersistence(started.request))
		assertEquals(listOf(false), callbacks)
		assertEquals(listOf("queued"), released)
	}

	@Test
	fun invalidationDoesNotReleaseValueWhileWorkerStillOwnsIt() {
		val released = mutableListOf<String>()
		val callbacks = mutableListOf<Boolean>()
		val ledger = ReaderPageRasterPublicationLedger<String>(released::add)
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "active", callbacks::add)
		)
		assertEquals("active", ledger.acquireForPersistence(started.request))

		ledger.invalidate()

		assertEquals(listOf(false), callbacks)
		assertTrue(released.isEmpty())
		assertFalse(ledger.complete(started.request, persisted = true))
		assertEquals(listOf("active"), released)
	}

	@Test
	fun pausedOldCompletionCannotSatisfySameDigestInNewEpoch() {
		val released = mutableListOf<String>()
		val results = mutableListOf<Pair<String, Boolean>>()
		val ledger = ReaderPageRasterPublicationLedger<String>(released::add)
		val old = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "old") { results += "old" to it }
		)
		assertEquals("old", ledger.acquireForPersistence(old.request))

		ledger.invalidate()
		val current = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "new") { results += "new" to it }
		)
		assertEquals("new", ledger.acquireForPersistence(current.request))

		assertFalse(ledger.complete(old.request, persisted = true))
		assertTrue(ledger.complete(current.request, persisted = true))
		assertEquals(listOf("old" to false, "new" to true), results)
		assertEquals(listOf("old", "new"), released)
	}

	@Test
	fun duplicateRegistrationCoalescesWithoutSchedulingReleasedValue() {
		val released = mutableListOf<String>()
		val callbacks = mutableListOf<Pair<String, Boolean>>()
		val scheduled = mutableListOf<ReaderPageRasterPublicationRequest>()
		val ledger = ReaderPageRasterPublicationLedger<String>(released::add)

		val first = ledger.begin("digest", "producer") {
			callbacks += "producer" to it
		}
		if (first is ReaderPageRasterPublicationRegistration.Started) {
			scheduled += first.request
		}
		val duplicate = ledger.begin("digest", "duplicate") {
			callbacks += "duplicate" to it
		}
		if (duplicate is ReaderPageRasterPublicationRegistration.Started) {
			scheduled += duplicate.request
		}

		val request =
			assertIs<ReaderPageRasterPublicationRegistration.Started>(first).request
		assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(duplicate)
		assertEquals(listOf(request), scheduled)
		assertEquals("producer", ledger.acquireForPersistence(request))
		assertTrue(ledger.complete(request, persisted = true))
		assertEquals(
			listOf("producer" to true, "duplicate" to true),
			callbacks
		)
		assertEquals(listOf("duplicate", "producer"), released)
	}

	@Test
	fun callbackAndReleaseFailuresDoNotStrandEntriesOrSuppressLaterDispatch() {
		val callbacks = mutableListOf<String>()
		val releases = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger<String> { value ->
			releases += value
			if (value == "producer") error("release-failed")
		}
		val started = assertIs<ReaderPageRasterPublicationRegistration.Started>(
			ledger.begin("digest", "producer") {
				callbacks += "first"
				error("callback-failed")
			}
		)
		assertIs<ReaderPageRasterPublicationRegistration.Coalesced>(
			ledger.begin("digest", "duplicate") {
				callbacks += "second"
			}
		)
		assertEquals("producer", ledger.acquireForPersistence(started.request))

		assertTrue(ledger.complete(started.request, persisted = true))

		assertEquals(listOf("first", "second"), callbacks)
		assertEquals(listOf("duplicate", "producer"), releases)
		assertEquals(0, ledger.entryCount())
		assertNotNull(ledger.dispatchFailure())
	}

	@Test
	fun invalidationAttemptsEveryCallbackAndQueuedReleaseAfterFailure() {
		val events = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger<String> { value ->
			events += "release-$value"
			if (value == "a") error("release-a")
		}
		ledger.begin("a", "a") {
			events += "callback-a"
			error("callback-a")
		}
		ledger.begin("b", "b") {
			events += "callback-b"
		}

		ledger.invalidate()

		assertTrue("callback-a" in events)
		assertTrue("callback-b" in events)
		assertTrue("release-a" in events)
		assertTrue("release-b" in events)
		assertEquals(0, ledger.entryCount())
		assertNotNull(ledger.dispatchFailure())
	}

	@Test
	fun configuredCapacityRejectsAndReleasesWithoutAddingOwnership() {
		val events = mutableListOf<String>()
		val ledger = ReaderPageRasterPublicationLedger<String>(
			release = { events += "release-$it" },
			maxEntries = 1,
			maxCallbacksPerEntry = 1
		)
		ledger.begin("first", "first") { events += "first-$it" }

		assertEquals(
			ReaderPageRasterPublicationRegistration.Rejected(
				ReaderPageRasterPublicationRejection.CallbackCapacity
			),
			ledger.begin("first", "duplicate") {
				events += "duplicate-$it"
			}
		)
		assertEquals(
			ReaderPageRasterPublicationRegistration.Rejected(
				ReaderPageRasterPublicationRejection.EntryCapacity
			),
			ledger.begin("second", "second") { events += "second-$it" }
		)
		assertEquals(1, ledger.entryCount())
		assertEquals(
			listOf(
				"duplicate-false",
				"release-duplicate",
				"second-false",
				"release-second"
			),
			events
		)
	}
}
