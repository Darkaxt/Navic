package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPageRasterDeferralPolicyTest {
	@Test
	fun everyDeferralRetriesOnlyOnItsMatchingEvent() {
		val cases = mapOf(
			ReaderPageRasterDeferralReason.ContentNotReady to
				ReaderPageRasterRetryEvent.ContentReady,
			ReaderPageRasterDeferralReason.LayoutUnstable to
				ReaderPageRasterRetryEvent.LayoutStable,
			ReaderPageRasterDeferralReason.PaginationNotReady to
				ReaderPageRasterRetryEvent.PaginationReady,
			ReaderPageRasterDeferralReason.WebViewDetached to
				ReaderPageRasterRetryEvent.WebViewAttached,
			ReaderPageRasterDeferralReason.ReaderPaused to
				ReaderPageRasterRetryEvent.ReaderResumed,
			ReaderPageRasterDeferralReason.PassiveHostUnavailable to
				ReaderPageRasterRetryEvent.PassiveHostAvailable,
			ReaderPageRasterDeferralReason.CanonicalLiveCommitUnavailable to
				ReaderPageRasterRetryEvent.CanonicalLiveCommitIssued
		)
		assertEquals(ReaderPageRasterDeferralReason.entries.toSet(), cases.keys)

		cases.forEach { (reason, expectedEvent) ->
			val policy = ReaderPageRasterDeferralPolicy(reason)
			ReaderPageRasterRetryEvent.entries.forEach { event ->
				assertEquals(event == expectedEvent, policy.shouldRetry(event), "$reason -> $event")
			}
		}
	}

	@Test
	fun replacementAndCloseCancelEachDeferredSessionExactlyOnce() {
		val cancelled = mutableListOf<Long>()
		val coordinator = ReaderPageRasterDeferredRetryCoordinator()
		val firstReason = ReaderPageRasterDeferralReason.ContentNotReady
		coordinator.defer(
			sessionId = 1L,
			reason = firstReason,
			observedVersion = coordinator.observeVersion(firstReason),
			retry = {},
			cancel = { cancelled += 1L }
		)
		val secondReason = ReaderPageRasterDeferralReason.ReaderPaused
		coordinator.defer(
			sessionId = 2L,
			reason = secondReason,
			observedVersion = coordinator.observeVersion(secondReason),
			retry = {},
			cancel = { cancelled += 2L }
		)

		coordinator.cancelAll()
		coordinator.cancelAll()

		assertEquals(listOf(1L, 2L), cancelled)
	}

	@Test
	fun eventBetweenReadinessCheckAndRegistrationRetriesImmediately() {
		val coordinator = ReaderPageRasterDeferredRetryCoordinator()
		val reason = ReaderPageRasterDeferralReason.LayoutUnstable
		val observed = coordinator.observeVersion(reason)
		var retries = 0

		assertFalse(coordinator.onRetryEvent(ReaderPageRasterRetryEvent.LayoutStable))
		coordinator.defer(
			sessionId = 9L,
			reason = reason,
			observedVersion = observed,
			retry = { retries += 1 },
			cancel = { error("Immediate retry must not register") }
		)

		assertEquals(1, retries)
		assertEquals(0, coordinator.deferredCount())
	}

	@Test
	fun unrelatedEventAdvancesItsVersionWithoutConsumingRequest() {
		val coordinator = ReaderPageRasterDeferredRetryCoordinator()
		val reason = ReaderPageRasterDeferralReason.WebViewDetached
		var retries = 0
		coordinator.defer(
			sessionId = 3L,
			reason = reason,
			observedVersion = coordinator.observeVersion(reason),
			retry = { retries += 1 },
			cancel = { error("Unrelated event must not cancel") }
		)

		assertFalse(coordinator.onRetryEvent(ReaderPageRasterRetryEvent.ContentReady))
		assertEquals(1, coordinator.deferredCount())
		assertTrue(coordinator.onRetryEvent(ReaderPageRasterRetryEvent.WebViewAttached))
		assertEquals(1, retries)
		assertEquals(0, coordinator.deferredCount())
	}

	@Test
	fun sessionQualifiedCancellationCannotCancelReplacement() {
		val coordinator = ReaderPageRasterDeferredRetryCoordinator()
		val reason = ReaderPageRasterDeferralReason.ReaderPaused
		var cancelled = 0
		coordinator.defer(
			sessionId = 12L,
			reason = reason,
			observedVersion = coordinator.observeVersion(reason),
			retry = {},
			cancel = { cancelled += 1 }
		)

		assertFalse(coordinator.cancel(11L))
		assertEquals(1, coordinator.deferredCount())
		assertTrue(coordinator.cancel(12L))
		assertEquals(1, cancelled)
		assertEquals(0, coordinator.deferredCount())
	}
}
