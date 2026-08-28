package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPageRasterHostEventBridgeTest {
	@Test
	fun paginationStatusMapsToTypedReadiness() {
		val readiness = listOf(null, "measuring", "cached", "ready", "failed").map(
			::readerPagePaginationReadiness
		)

		assertEquals(
			listOf(
				ReaderPagePaginationReadiness.Loading,
				ReaderPagePaginationReadiness.Loading,
				ReaderPagePaginationReadiness.Cached,
				ReaderPagePaginationReadiness.Ready,
				ReaderPagePaginationReadiness.Failed
			),
			readiness
		)
		assertEquals(
			listOf(false, false, true, true, false),
			readiness.map { value -> value.isReadyForRasterization }
		)
	}

	@Test
	fun rasterReadinessRequiresAnActivePaginationProfile() {
		assertEquals(
			ReaderPagePaginationReadiness.Loading,
			readerPageActivePaginationReadiness(
				profileAvailable = false,
				readiness = ReaderPagePaginationReadiness.Ready
			)
		)
		assertEquals(
			ReaderPagePaginationReadiness.Cached,
			readerPageActivePaginationReadiness(
				profileAvailable = true,
				readiness = ReaderPagePaginationReadiness.Cached
			)
		)
	}

	@Test
	fun profileLossRearmsAnUnchangedReadyPaginationStatus() {
		val published = mutableListOf<ReaderPageRasterRetryEvent>()
		val bridge = ReaderPageRasterHostEventBridge(published::add)
		val paginationReadiness = ReaderPagePaginationReadiness.Ready

		listOf(true, false, true).forEach { profileAvailable ->
			bridge.paginationReadinessChanged(
				readerPageActivePaginationReadiness(
					profileAvailable = profileAvailable,
					readiness = paginationReadiness
				)
			)
		}

		assertEquals(
			listOf(
				ReaderPageRasterRetryEvent.PaginationReady,
				ReaderPageRasterRetryEvent.PaginationReady
			),
			published
		)
	}

	@Test
	fun productionHostEventControllerRoutesAllFiveEdgesIntoOneShotRetry() {
		val retried = mutableListOf<ReaderPageRasterRetryEvent>()
		val coordinator = ReaderPageRasterDeferredRetryCoordinator()
		val events = ReaderPageRasterHostEventController(
			onRetryEvent = coordinator::onRetryEvent,
			cancelAllDeferredRetries = coordinator::cancelAll
		)
		val layout = ReaderPageLayoutSignature(1080, 2200, 0, 17L)
		val cases = listOf(
			Triple(
				ReaderPageRasterDeferralReason.ContentNotReady,
				ReaderPageRasterRetryEvent.ContentReady
			) {
				events.contentReadyKeyChanged("ready-1")
				events.contentReadyKeyChanged("ready-1")
			},
			Triple(
				ReaderPageRasterDeferralReason.LayoutUnstable,
				ReaderPageRasterRetryEvent.LayoutStable
			) {
				events.layoutSignatureMeasured(layout)
				events.layoutSignatureMeasured(layout)
				events.layoutSignatureMeasured(layout)
			},
			Triple(
				ReaderPageRasterDeferralReason.PaginationNotReady,
				ReaderPageRasterRetryEvent.PaginationReady
			) {
				events.paginationReadinessChanged(ReaderPagePaginationReadiness.Cached)
				events.paginationReadinessChanged(ReaderPagePaginationReadiness.Ready)
			},
			Triple(
				ReaderPageRasterDeferralReason.WebViewDetached,
				ReaderPageRasterRetryEvent.WebViewAttached
			) {
				events.webViewAttachmentChanged(true)
				events.webViewAttachmentChanged(true)
			},
			Triple(
				ReaderPageRasterDeferralReason.ReaderPaused,
				ReaderPageRasterRetryEvent.ReaderResumed
			) {
				events.lifecycleResumedChanged(true)
				events.lifecycleResumedChanged(true)
			}
		)

		cases.forEachIndexed { index, (reason, event, publish) ->
			coordinator.defer(
				sessionId = index.toLong(),
				reason = reason,
				observedVersion = coordinator.observeVersion(reason),
				retry = { retried += event },
				cancel = { error("Matched request must not cancel") }
			)
			publish()
		}

		assertEquals(cases.map { (_, event) -> event }, retried)
		assertEquals(0, coordinator.deferredCount())

		var cancelled = 0
		val closeReason = ReaderPageRasterDeferralReason.ReaderPaused
		coordinator.defer(
			sessionId = 99L,
			reason = closeReason,
			observedVersion = coordinator.observeVersion(closeReason),
			retry = { error("Closed host event controller retried") },
			cancel = { cancelled += 1 }
		)
		events.close()
		events.close()
		assertEquals(1, cancelled)
		assertEquals(0, coordinator.deferredCount())
	}

	@Test
	fun hostControllerForwardsAttachmentStateToBackgroundOwnership() {
		val attachments = mutableListOf<Boolean>()
		val events = ReaderPageRasterHostEventController(
			onRetryEvent = {},
			cancelAllDeferredRetries = {},
			onWebViewAttachmentChanged = attachments::add
		)

		events.webViewAttachmentChanged(false)
		events.webViewAttachmentChanged(true)
		events.webViewAttachmentChanged(true)
		events.close()

		assertEquals(listOf(false, true, true, false), attachments)
	}

	@Test
	fun contentAndLayoutEdgesRearmAfterBecomingNotReadyOrUnstable() {
		val published = mutableListOf<ReaderPageRasterRetryEvent>()
		val bridge = ReaderPageRasterHostEventBridge(published::add)
		val first = ReaderPageLayoutSignature(1080, 2200, 0, 17L)
		val second = ReaderPageLayoutSignature(2200, 1080, 0, 18L)

		bridge.contentReadyKeyChanged("chapter-a")
		bridge.contentReadyKeyChanged("chapter-a")
		bridge.contentReadyKeyChanged(null)
		bridge.contentReadyKeyChanged("chapter-a")
		bridge.layoutSignatureMeasured(first)
		bridge.layoutSignatureMeasured(first)
		bridge.layoutSignatureMeasured(first)
		bridge.layoutSignatureMeasured(second)
		bridge.layoutSignatureMeasured(first)
		bridge.layoutSignatureMeasured(first)

		assertEquals(
			listOf(
				ReaderPageRasterRetryEvent.ContentReady,
				ReaderPageRasterRetryEvent.ContentReady,
				ReaderPageRasterRetryEvent.LayoutStable,
				ReaderPageRasterRetryEvent.LayoutStable
			),
			published
		)
	}

	@Test
	fun explicitLayoutInvalidationRearmsTheSameProfileSignature() {
		val published = mutableListOf<ReaderPageRasterRetryEvent>()
		val bridge = ReaderPageRasterHostEventBridge(published::add)
		val layout = ReaderPageLayoutSignature(1080, 2200, 0, 17L)

		bridge.layoutSignatureMeasured(layout)
		bridge.layoutSignatureMeasured(layout)
		bridge.layoutStabilityInvalidated()
		bridge.layoutSignatureMeasured(layout)
		bridge.layoutSignatureMeasured(layout)

		assertEquals(
			listOf(
				ReaderPageRasterRetryEvent.LayoutStable,
				ReaderPageRasterRetryEvent.LayoutStable
			),
			published
		)
	}

	@Test
	fun readinessAttachmentAndLifecyclePublishOnlyRisingEdges() {
		val published = mutableListOf<ReaderPageRasterRetryEvent>()
		val bridge = ReaderPageRasterHostEventBridge(published::add)

		bridge.paginationReadinessChanged(ReaderPagePaginationReadiness.Loading)
		bridge.paginationReadinessChanged(ReaderPagePaginationReadiness.Cached)
		bridge.paginationReadinessChanged(ReaderPagePaginationReadiness.Ready)
		bridge.paginationReadinessChanged(ReaderPagePaginationReadiness.Failed)
		bridge.paginationReadinessChanged(ReaderPagePaginationReadiness.Ready)
		bridge.webViewAttachmentChanged(false)
		bridge.webViewAttachmentChanged(true)
		bridge.webViewAttachmentChanged(true)
		bridge.webViewAttachmentChanged(false)
		bridge.webViewAttachmentChanged(true)
		bridge.lifecycleResumedChanged(false)
		bridge.lifecycleResumedChanged(true)
		bridge.lifecycleResumedChanged(true)
		bridge.lifecycleResumedChanged(false)
		bridge.lifecycleResumedChanged(true)

		assertEquals(
			listOf(
				ReaderPageRasterRetryEvent.PaginationReady,
				ReaderPageRasterRetryEvent.PaginationReady,
				ReaderPageRasterRetryEvent.WebViewAttached,
				ReaderPageRasterRetryEvent.WebViewAttached,
				ReaderPageRasterRetryEvent.ReaderResumed,
				ReaderPageRasterRetryEvent.ReaderResumed
			),
			published
		)
	}
}
