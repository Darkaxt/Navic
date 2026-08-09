package paige.navic.ui.screens.reader

import java.io.File
import karacken.curl.PageSurfaceDisposalStage
import karacken.curl.PageSurfaceOwnershipResult
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageLifecycleCancellationReason
import paige.navic.reader.ReaderPagePointerOwnership
import paige.navic.reader.ReaderPageTurnDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPageDiagnosticTest {
	private val noQaFaultCorrelation =
		"qaFaultRequestId=none qaFaultRelation=None " +
			"qaFaultPublicationEpoch=-1 qaFaultPersistenceAttemptId=-1 " +
			"qaFaultRasterRequestEpoch=-1 qaFaultRepairAttemptId=-1 " +
			"qaFaultPreparationAttemptId=-1 qaFaultRelocationToken=none " +
			"qaFaultHandoffAttemptId=-1"
	private val sentinels = listOf(
		"PRIVATE_EPUB_TEXT",
		"PRIVATE_ANNOTATION",
		"PRIVATE_SELECTION",
		"PRIVATE_TRANSCRIPT",
		"PRIVATE_RASTER_PAYLOAD",
		"SECRET_TOKEN"
	)

	@Test
	fun gestureAndLifecycleEventsContainOnlyTypedReconstructionFields() {
		val gesture = ReaderPageDiagnostic.gesture(
			readerSession = 7L,
			gestureId = 12L,
			outcome = ReaderPageGestureTerminalOutcome.CompletedTapAction,
			owner = ReaderPagePointerOwnership.Content,
			rasterGeneration = 4L,
			textureGeneration = 9L,
			physicalDirection = null,
			logicalDirection = null,
			durationMs = 17L
		)
		assertEquals(
			"reader-gesture session=7 gestureId=12 outcome=CompletedTapAction " +
				"owner=Content rasterGeneration=4 textureGeneration=9 " +
				"physicalDirection=null logicalDirection=null durationMs=17",
			gesture
		)
		val cancellation = ReaderPageDiagnostic.lifecycleCancellation(
			readerSession = 7L,
			gestureId = 12L,
			reason = ReaderPageLifecycleCancellationReason.RasterProfileInvalidated
		)
		assertEquals(
			"reader-lifecycle-cancellation session=7 gestureId=12 " +
				"reason=RasterProfileInvalidated",
			cancellation
		)
		assertPrivateSentinelsAbsent(gesture, cancellation)
	}

	@Test
	fun handoffPreparationRepairAndDeckUseEnumsAndNumericIdentity() {
		val handoffResult = ReaderWebViewVisualHandoffResult.Failed(
			token = "token-9",
			reason = ReaderWebViewVisualHandoffFailure.CallbackCapacity
		).toDiagnosticResult()
		assertEquals(
			ReaderPageHandoffDiagnosticResult.CallbackCapacity,
			handoffResult
		)
		val messages = listOf(
			ReaderPageDiagnostic.handoff(
				readerSession = 7L,
				handoffAttemptId = 19L,
				token = "token-9",
				target = 31,
				visualState = false,
				nextFrame = false,
				result = handoffResult,
				durationMs = 3L
			),
			ReaderPageDiagnostic.rasterAcquisition(
				readerSession = 7L,
				attempt = 22L,
				rasterGeneration = 4L,
				ordinal = 31,
				source = ReaderPageRasterAcquisitionSource.PersistentHydration,
				trigger = ReaderPageRasterAcquisitionTrigger.WarmReopen,
				result = ReaderPageRasterAcquisitionResult.Hit,
				durationMs = 5L
			),
			ReaderPageDiagnostic.preparation(
				readerSession = 7L,
				attempt = 23L,
				rasterGeneration = 4L,
				state = ReaderPagePreparationDiagnosticState.Deferred,
				reason = ReaderPageRasterDeferralReason.LayoutUnstable,
				eventVersion = 9L,
				durationMs = 2L
			),
			ReaderPageDiagnostic.repair(
				readerSession = 7L,
				attempt = 24L,
				rasterGeneration = 4L,
				centerOrdinal = 31,
				state = ReaderPageRepairDiagnosticState.Ready,
				reason = null,
				durationMs = 8L
			),
			ReaderPageDiagnostic.deck(
				readerSession = 7L,
				generation = 9L,
				repairAttempt = 24L,
				role = ReaderDeckSubmissionRole.Active,
				prepared = true,
				active = 9L,
				pending = null,
				durationMs = 3L
			)
		)
		assertEquals(
			"reader-handoff session=7 token=token-9 handoffAttemptId=19 target=31 " +
				"visualState=false nextFrame=false result=CallbackCapacity durationMs=3 " +
				noQaFaultCorrelation,
			messages[0]
		)
		assertTrue(messages[1].contains("source=PersistentHydration trigger=WarmReopen result=Hit"))
		assertTrue(messages[2].contains("state=Deferred reason=LayoutUnstable eventVersion=9"))
		assertTrue(messages[3].contains("state=Ready reason=None"))
		assertTrue(messages[4].contains("repairAttempt=24 role=Active"))
		assertPrivateSentinelsAbsent(*messages.toTypedArray())
	}

	@Test
	fun relocationRejectionHasOneClosedTypedCause() {
		val line = ReaderPageDiagnostic.relocation(
			readerSession = 7L,
			token = "page-turn-31",
			gestureId = 61L,
			source = 16,
			target = 15,
			logicalDirection = ReaderPageTurnDirection.Previous,
			rasterGeneration = 4L,
			textureGeneration = 35L,
			state = ReaderPageRelocationDiagnosticState.Rejected,
			rejectionReason =
				ReaderPageRelocationDiagnosticRejectionReason.AcknowledgementTimeout,
			queueDepth = 0,
			durationMs = 10_003L
		)

		assertTrue(
			line.contains(
				"state=Rejected rejectionReason=AcknowledgementTimeout queueDepth=0"
			)
		)
		assertFailsWith<IllegalArgumentException> {
			ReaderPageDiagnostic.relocation(
				readerSession = 7L,
				token = "page-turn-31",
				gestureId = 61L,
				source = 16,
				target = 15,
				logicalDirection = ReaderPageTurnDirection.Previous,
				rasterGeneration = 4L,
				textureGeneration = 35L,
				state = ReaderPageRelocationDiagnosticState.Completed,
				rejectionReason =
					ReaderPageRelocationDiagnosticRejectionReason.AcknowledgementTimeout,
				queueDepth = 0,
				durationMs = 10_003L
			)
		}
		assertPrivateSentinelsAbsent(line)
	}

	@Test
	fun relocationOwnershipRejectionsRemainClosedAndBounded() {
		assertEquals(
			listOf(
				"None",
				"CommitPublicationFailed",
				"QueueInvalidated",
				"AcknowledgementTimeout",
				"JavascriptDispatchFailed",
				"ContentRejected",
				"OwnershipUnavailable",
				"OwnershipInvalidated",
				"WebViewUnavailable"
			),
			ReaderPageRelocationDiagnosticRejectionReason.entries.map { it.name }
		)
		listOf(
			ReaderPageRelocationDiagnosticRejectionReason.OwnershipUnavailable,
			ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated,
			ReaderPageRelocationDiagnosticRejectionReason.WebViewUnavailable
		).forEach { reason ->
			val line = ReaderPageDiagnostic.relocation(
				readerSession = 7L,
				token = "page-turn-31",
				gestureId = 61L,
				source = 16,
				target = 15,
				logicalDirection = ReaderPageTurnDirection.Previous,
				rasterGeneration = 4L,
				textureGeneration = 35L,
				state = ReaderPageRelocationDiagnosticState.Rejected,
				rejectionReason = reason,
				queueDepth = 0,
				durationMs = 3L
			)
			assertTrue(line.contains("rejectionReason=$reason"))
			assertPrivateSentinelsAbsent(line)
		}
	}

	@Test
	fun qaFaultProjectionContainsOnlyClosedReconstructableFields() {
		val line = ReaderPageDiagnostic.qaFault(
			readerSession = 42L,
			event = ReaderPageQaFaultEvent(
				ticket = ReaderPageQaFaultTicket(
					requestId = "fault-request",
					fault = ReaderPageQaFault.DelayNextVisualStateCallback
				),
				seam = "visual-state",
				state = ReaderPageQaFaultState.Released,
				operation = ReaderPageQaFaultOperationContext(
					relocationToken = "relocation-7",
					handoffAttemptId = 9L
				),
				releaseRequestId = "release-request",
				result = "command-release"
			)
		)

		assertEquals(
			"reader-qa-fault session=42 requestId=fault-request " +
				"fault=DelayNextVisualStateCallback seam=visual-state " +
				"state=Released publicationEpoch=-1 persistenceAttemptId=-1 " +
				"rasterRequestEpoch=-1 repairAttemptId=-1 " +
				"preparationAttemptId=-1 relocationToken=relocation-7 " +
				"handoffAttemptId=9 releaseRequestId=release-request " +
				"result=command-release",
			line
		)
		listOf("PRIVATE_EPUB_TEXT", "SECRET_TOKEN", "href=", "cfi=")
			.forEach { assertFalse(line.contains(it)) }
	}

	@Test
	fun correlatedDiagnosticsSerializeTheImmutableAppliedRoot() {
		val correlation = ReaderPageQaFaultCorrelation(
			requestId = "raster-miss",
			appliedOperation = ReaderPageQaFaultOperationContext(
				rasterRequestEpoch = 17L
			),
			relation = ReaderPageQaFaultRelation.Recovery
		)

		val line = ReaderPageDiagnostic.repair(
			readerSession = 7L,
			attempt = 25L,
			rasterGeneration = 4L,
			centerOrdinal = 31,
			state = ReaderPageRepairDiagnosticState.Ready,
			reason = null,
			durationMs = 8L,
			qaFaultCorrelation = correlation
		)

		assertTrue(line.contains("attempt=25"))
		assertTrue(line.contains("qaFaultRequestId=raster-miss"))
		assertTrue(line.contains("qaFaultRelation=Recovery"))
		assertTrue(line.contains("qaFaultRasterRequestEpoch=17"))
		assertTrue(line.contains("qaFaultRepairAttemptId=-1"))
		assertPrivateSentinelsAbsent(line)
	}

	@Test
	fun teardownNeverIncludesExceptionMessages() {
		val teardown = ReaderPageTeardownException(
			stage = ReaderPageTeardownStage.RendererDisposal,
			rendererStage = PageSurfaceDisposalStage.GL_RENDERER_DISPOSE,
			cause = IllegalStateException("PRIVATE_EPUB_TEXT SECRET_TOKEN")
		).apply {
			addSuppressed(
				ReaderPageTeardownException(
					ReaderPageTeardownStage.PersistentStore,
					cause = IllegalStateException("PRIVATE_ANNOTATION")
				)
			)
		}

		val message = ReaderPageDiagnostic.teardownFailure(7L, teardown)

		assertEquals(
			"reader-teardown-failure session=7 stage=RendererDisposal " +
				"rendererStage=GL_RENDERER_DISPOSE suppressed=1",
			message
		)
		assertPrivateSentinelsAbsent(message)
	}

	@Test
	fun ownerDerivedMetricsRenderCountsAndBoundsWithoutPayloads() {
		val residency = ReaderPageDiagnostic.residency(
			7L,
			ReaderPlayLikeCurlRasterResidencyMetrics(
				residentEntries = 2,
				uniqueDecodedBitmaps = 2,
				residentEntryLimit = 12,
				uniqueDecodedBitmapLimit = 12,
				peakResidentEntries = 4,
				peakUniqueDecodedBitmaps = 4,
				pinnedEntries = 1,
				activePreparationWorkers = 0,
				activeMaterializationWorkers = 0,
				pendingValueReleases = 1,
				evictedEntries = 3,
				releasedEntries = 5
			)
		)
		val cache = ReaderPageDiagnostic.rasterCache(
			7L,
			ReaderPageOwnershipPhase.PeakPreparation,
			ReaderPageRasterCacheMetrics(
				diskEntries = 3,
				diskBytes = 100,
				diskByteLimit = 1_000,
				decodedEntries = 2,
				uniqueDecodedBitmaps = 2,
				uniqueDecodedBitmapLimit = 5,
				pendingDecodedReleases = 1,
				activeEncodePins = 1,
				encodePinnedIdentities = 1
			)
		)
		assertTrue(residency.contains("pendingReleases=1"))
		assertTrue(cache.contains("diskByteLimit=1000"))
		assertTrue(cache.contains("activeEncodePins=1 encodePinnedIdentities=1"))
		assertPrivateSentinelsAbsent(residency, cache)
	}

	@Test
	fun ownershipAndUnavailableEventsRemainStructured() {
		val snapshot = ReaderPageOwnershipSnapshot(
			adapterResidents = 1,
			adapterDecodedBitmaps = 1,
			cacheDecodedBitmaps = 1,
			stagedPublications = 1,
			activeDeckLeases = 1,
			pendingDeckLeases = 0,
			releaseInFlightDeckLeases = 1,
			orphanDeckLeases = 0,
			rendererTextures = 2,
			pendingCallbacks = 1,
			foregroundPassiveOwners = 1,
			foregroundLiveClaims = 3,
			foregroundRestorationCallbacks = 1,
			relocationReservations = 1,
			queuedRelocations = 0,
			relocationTokens = 1,
			bounds = ReaderPageOwnershipBounds(
				adapterResidentLimit = 2,
				adapterDecodedBitmapLimit = 2,
				cacheDecodedBitmapLimit = 2,
				stagedPublicationLimit = 2,
				activeDeckLeaseLimit = 1,
				pendingDeckLeaseLimit = 1,
				releaseInFlightDeckLeaseLimit = 4,
				orphanDeckLeaseLimit = 0,
				rendererTextureLimit = 8,
				pendingCallbackLimit = 16,
					foregroundPassiveOwnerLimit = 1,
					foregroundLiveClaimLimit = 4,
					foregroundRestorationCallbackLimit = 1,
				relocationTokenLimit = 4
			)
		)
		val ownership = ReaderPageDiagnostic.ownership(
			7L,
			ReaderPageOwnershipPhase.PeakPreparation,
			snapshot
		)
		assertTrue(ownership.contains("releaseInFlightLeases=1"))
		assertTrue(ownership.contains("relocationReservations=1"))
		assertTrue(ownership.contains("foregroundPassiveOwners=1"))
		assertTrue(ownership.contains("foregroundPassiveOwnerLimit=1"))
		assertTrue(ownership.contains("foregroundLiveClaims=3"))
		assertTrue(ownership.contains("foregroundLiveClaimLimit=4"))
		assertTrue(ownership.contains("foregroundRestorationCallbacks=1"))
		assertTrue(ownership.contains("foregroundRestorationCallbackLimit=1"))
		assertTrue(ownership.contains("withinBounds=true"))
		assertEquals(
			"reader-ownership-unavailable session=7 phase=after-close " +
				"status=SURFACE_UNAVAILABLE",
			ReaderPageDiagnostic.ownershipUnavailable(
				7L,
				ReaderPageOwnershipPhase.AfterClose,
				ReaderPageOwnershipUnavailableReason.Renderer(
					PageSurfaceOwnershipResult.Status.SURFACE_UNAVAILABLE
				)
			)
		)
		assertEquals(
			"reader-ownership-unavailable session=7 phase=steady-state " +
				"status=APPLICATION_EPOCH_CHANGED",
			ReaderPageDiagnostic.ownershipUnavailable(
				7L,
				ReaderPageOwnershipPhase.SteadyState,
				ReaderPageOwnershipUnavailableReason.ApplicationEpochChanged
			)
		)
		assertPrivateSentinelsAbsent(ownership)
	}

	@Test
	fun retryOperationAllocatesFreshAttemptWithoutMutatingAppliedRoot() {
		val messages = mutableListOf<String>()
		val diagnostics = ReaderPageRuntimeDiagnostics(
			readerSession = 9L,
			nowMs = { 100L },
			emit = messages::add
		)
		val root = diagnostics.startOperation(
			rasterGeneration = 3L,
			ordinal = 4,
			qaFaultCorrelation = ReaderPageQaFaultCorrelation(
				requestId = "deferred-preparation",
				appliedOperation = ReaderPageQaFaultOperationContext(
					preparationAttemptId = 17L
				),
				relation = ReaderPageQaFaultRelation.AppliedOperation
			)
		)

		val retry = diagnostics.startRetryOperation(
			root = root,
			rasterGeneration = 3L,
			ordinal = 4
		)
		diagnostics.preparation(
			root,
			ReaderPagePreparationDiagnosticState.Resumed,
			ReaderPageRasterDeferralReason.ContentNotReady,
			eventVersion = 2L
		)
		diagnostics.preparation(
			retry,
			ReaderPagePreparationDiagnosticState.Failed,
			ReaderPageRasterDeferralReason.ContentNotReady
		)

		assertTrue(retry.attempt > root.attempt)
		assertEquals(
			ReaderPageQaFaultRelation.AppliedOperation,
			root.qaFaultCorrelation?.relation
		)
		assertEquals(ReaderPageQaFaultRelation.Retry, retry.qaFaultCorrelation?.relation)
		assertTrue(messages[0].contains("attempt=${root.attempt}"))
		assertTrue(messages[0].contains("state=Resumed"))
		assertTrue(messages[0].contains("qaFaultRelation=AppliedOperation"))
		assertTrue(messages[1].contains("attempt=${retry.attempt}"))
		assertTrue(messages[1].contains("state=Failed"))
		assertTrue(messages[1].contains("qaFaultRelation=Retry"))
	}

	@Test
	fun publicationDiagnosticsRequireTypedNonnegativePersistenceAttempts() {
		assertFailsWith<IllegalArgumentException> {
			ReaderPagePersistenceAttemptId(-1L)
		}
		val line = ReaderPageDiagnostic.publication(
			readerSession = 9L,
			digestPrefix = "0123456789ab",
			rasterEpoch = 3L,
			persistenceAttemptId = ReaderPagePersistenceAttemptId(0L),
			result = ReaderPagePublicationDiagnosticResult.Failed,
			durationMs = 0L
		)
		assertTrue(line.contains("persistenceAttemptId=0"))
	}

	@Test
	fun publicationIdentityIsAllocatedBeforeSynchronousLedgerCallbacks() {
		val source = readerProductionSource("ReaderPageTurnBundleSource.android.kt")
		val publication = source.substringAfter(
			"val publicationEpoch = publicationLedger.currentEpoch()"
		).substringBefore("publicationValueTransferred = true")
		val allocation = publication.indexOf(
			"val persistenceAttemptId = ReaderPagePersistenceAttemptId("
		)
		val registration = publication.indexOf("val registration = publicationLedger.begin(")

		assertTrue(allocation >= 0, "A typed persistence attempt must be allocated")
		assertTrue(
			allocation < registration,
			"Synchronous coalesced/rejected callbacks must see their allocated identity"
		)
		assertFalse(publication.contains("var persistenceAttemptId"))
	}

	@Test
	fun runtimeOperationsAreMonotonicBoundedAndBestEffort() {
		var now = 100L
		val messages = mutableListOf<String>()
		val diagnostics = ReaderPageRuntimeDiagnostics(
			readerSession = 9L,
			nowMs = { now },
			emit = { message ->
				messages += message
				if (messages.size == 1) error("diagnostic-sink-failed")
			}
		)
		val first = diagnostics.startOperation(rasterGeneration = 3L, ordinal = 4)
		val second = diagnostics.startOperation(rasterGeneration = 3L, ordinal = 5)
		assertTrue(second.attempt > first.attempt)

		now = 90L
		diagnostics.rasterAcquisition(
			operation = first,
			source = ReaderPageRasterAcquisitionSource.PersistentHydration,
			trigger = ReaderPageRasterAcquisitionTrigger.WarmReopen,
			result = ReaderPageRasterAcquisitionResult.Hit
		)
		diagnostics.publication(
			digest = "PRIVATE_EPUB_TEXT",
			rasterEpoch = 3L,
			persistenceAttemptId = ReaderPagePersistenceAttemptId(1L),
			result = ReaderPagePublicationDiagnosticResult.Failed,
			startedAtMs = 100L
		)

		assertEquals(2, messages.size)
		assertTrue(messages[0].contains("durationMs=0"))
		assertTrue(messages[1].contains("digestPrefix=invalid"))
		assertPrivateSentinelsAbsent(*messages.toTypedArray())
	}

	@Test
	fun deckTrackerPublishesAuthoritativePreparedTransitionExactlyOnce() {
		var now = 100L
		val messages = mutableListOf<String>()
		val tracker = ReaderPageDeckDiagnosticTracker(
			ReaderPageRuntimeDiagnostics(
				readerSession = 7L,
				nowMs = { now },
				emit = messages::add
			)
		)
		tracker.begin(
			generation = 9L,
			repairAttempt = 24L,
			role = ReaderDeckSubmissionRole.Active
		)
		assertTrue(tracker.submitted(generation = 9L, active = 9L, pending = null))
		now = 103L

		assertTrue(tracker.prepared(generation = 9L, active = 9L, pending = null))
		assertFalse(tracker.prepared(generation = 9L, active = 9L, pending = null))

		assertEquals(0, tracker.pendingCount())
		assertEquals(2, messages.size)
		assertEquals(
			"reader-deck session=7 generation=9 repairAttempt=24 role=Active " +
				"prepared=false active=9 pending=null durationMs=0 " +
				noQaFaultCorrelation,
			messages[0]
		)
		assertEquals(
			"reader-deck session=7 generation=9 repairAttempt=24 role=Active " +
				"prepared=true active=9 pending=null durationMs=3 " +
				noQaFaultCorrelation,
			messages[1]
		)
	}

	@Test
	fun availableRendererStatusCannotBeReportedAsUnavailable() {
		assertFailsWith<IllegalArgumentException> {
			ReaderPageDiagnostic.ownershipUnavailable(
				readerSession = 7L,
				phase = ReaderPageOwnershipPhase.SteadyState,
				reason = ReaderPageOwnershipUnavailableReason.Renderer(
					PageSurfaceOwnershipResult.Status.AVAILABLE
				)
			)
		}
	}

	private fun assertPrivateSentinelsAbsent(vararg messages: String) {
		messages.forEach { message ->
			sentinels.forEach { sentinel ->
				assertFalse(message.contains(sentinel), "$sentinel leaked in $message")
			}
		}
	}
}

private fun readerProductionSource(fileName: String): String {
	var current: File? = File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
	repeat(10) {
		val root = current ?: return@repeat
		val candidate = File(
			root,
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/$fileName"
		)
		if (candidate.isFile) return candidate.readText()
		current = root.parentFile
	}
	error("Could not locate $fileName")
}
