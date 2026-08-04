package paige.navic.ui.screens.reader

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import karacken.curl.PageSurfaceOwnershipResult
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageLifecycleCancellationReason
import paige.navic.reader.ReaderPagePointerOwnership
import paige.navic.reader.ReaderPageTurnDirection

internal enum class ReaderPagePhysicalDirection {
	Left,
	Right
}

internal enum class ReaderPagePublicationDiagnosticResult {
	Durable,
	CapacityReached,
	Failed,
	Stale,
	Cancelled
}

@JvmInline
internal value class ReaderPagePersistenceAttemptId(val value: Long) {
	init {
		require(value >= 0L)
	}
}

internal enum class ReaderPageRasterAcquisitionSource {
	PersistentHydration,
	WebViewCapture
}

internal enum class ReaderPageRasterAcquisitionTrigger {
	InitialPreparation,
	WarmReopen,
	WorkingSetRefill,
	Repair
}

internal enum class ReaderPageRasterAcquisitionResult {
	Started,
	Hit,
	Miss,
	Durable,
	CapacityReached,
	Failed,
	Stale,
	Cancelled
}

internal enum class ReaderPagePreparationDiagnosticState {
	Attempted,
	Deferred,
	Resumed,
	Ready,
	Failed,
	Cancelled
}

internal enum class ReaderPageRepairDiagnosticState {
	Started,
	Deferred,
	Ready,
	Submitted,
	Completed,
	Failed,
	Cancelled
}

internal enum class ReaderPageRelocationDiagnosticState {
	Queued,
	Dispatched,
	Acknowledged,
	AwaitingVisualHandoff,
	Completed,
	Rejected
}

internal enum class ReaderPageRelocationDiagnosticRejectionReason {
	None,
	CommitPublicationFailed,
	QueueInvalidated,
	AcknowledgementTimeout,
	JavascriptDispatchFailed,
	ContentRejected
}

internal enum class ReaderPageHandoffDiagnosticResult {
	Ready,
	Detached,
	TimedOut,
	Invalidated,
	CallbackCapacity,
	ContentRejected,
	Cancelled,
	StalePhysicalCallbackReleased
}

internal fun ReaderWebViewVisualHandoffResult.toDiagnosticResult():
	ReaderPageHandoffDiagnosticResult = when (this) {
	is ReaderWebViewVisualHandoffResult.Ready ->
		ReaderPageHandoffDiagnosticResult.Ready
	is ReaderWebViewVisualHandoffResult.Failed -> when (reason) {
		ReaderWebViewVisualHandoffFailure.Detached ->
			ReaderPageHandoffDiagnosticResult.Detached
		ReaderWebViewVisualHandoffFailure.TimedOut ->
			ReaderPageHandoffDiagnosticResult.TimedOut
		ReaderWebViewVisualHandoffFailure.Invalidated ->
			ReaderPageHandoffDiagnosticResult.Invalidated
		ReaderWebViewVisualHandoffFailure.CallbackCapacity ->
			ReaderPageHandoffDiagnosticResult.CallbackCapacity
		ReaderWebViewVisualHandoffFailure.ContentRejected ->
			ReaderPageHandoffDiagnosticResult.ContentRejected
		ReaderWebViewVisualHandoffFailure.Cancelled ->
			ReaderPageHandoffDiagnosticResult.Cancelled
	}
}

internal enum class ReaderPagePrefetchDiagnosticState {
	Queued,
	Running,
	Completed,
	CapacityReached,
	Cancelled,
	Failed
}

internal enum class ReaderPageOwnershipPhase(
	val wireName: String
) {
	ColdStartBaseline("cold-start"),
	PeakPreparation("peak-preparation"),
	SteadyState("steady-state"),
	AfterClose("after-close")
}

internal data class ReaderPageDiagnosticOperation(
	val attempt: Long,
	val rasterGeneration: Long,
	val ordinal: Int,
	val startedAtMs: Long,
	val qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
)

private val ReaderPageDiagnosticAttemptIds = AtomicLong()

private fun diagnosticDigestPrefix(digest: String): String =
	digest.take(12).takeIf { prefix ->
		prefix.length == 12 && prefix.all { character ->
			character in '0'..'9' || character in 'a'..'f' || character in 'A'..'F'
		}
	} ?: "invalid"

internal class ReaderPageRuntimeDiagnostics(
	private val readerSession: Long,
	private val nowMs: () -> Long = SystemClock::uptimeMillis,
	private val emit: (String) -> Unit
) {
	fun startOperation(
		rasterGeneration: Long,
		ordinal: Int,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	): ReaderPageDiagnosticOperation = ReaderPageDiagnosticOperation(
		attempt = ReaderPageDiagnosticAttemptIds.incrementAndGet(),
		rasterGeneration = rasterGeneration,
		ordinal = ordinal,
		startedAtMs = nowMs(),
		qaFaultCorrelation = qaFaultCorrelation
	)

	fun startRetryOperation(
		root: ReaderPageDiagnosticOperation,
		rasterGeneration: Long,
		ordinal: Int
	): ReaderPageDiagnosticOperation = startOperation(
		rasterGeneration = rasterGeneration,
		ordinal = ordinal,
		qaFaultCorrelation = root.qaFaultCorrelation?.withRelation(
			ReaderPageQaFaultRelation.Retry
		)
	)

	fun publication(
		digest: String,
		rasterEpoch: Long,
		persistenceAttemptId: ReaderPagePersistenceAttemptId,
		result: ReaderPagePublicationDiagnosticResult,
		startedAtMs: Long,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	) = publish(
		ReaderPageDiagnostic.publication(
			readerSession = readerSession,
			digestPrefix = diagnosticDigestPrefix(digest),
			rasterEpoch = rasterEpoch,
			persistenceAttemptId = persistenceAttemptId,
			result = result,
			durationMs = elapsed(startedAtMs),
			qaFaultCorrelation = qaFaultCorrelation
		)
	)

	fun rasterAcquisition(
		operation: ReaderPageDiagnosticOperation,
		source: ReaderPageRasterAcquisitionSource,
		trigger: ReaderPageRasterAcquisitionTrigger,
		result: ReaderPageRasterAcquisitionResult,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	) = publish(
		ReaderPageDiagnostic.rasterAcquisition(
			readerSession = readerSession,
			attempt = operation.attempt,
			rasterGeneration = operation.rasterGeneration,
			ordinal = operation.ordinal,
			source = source,
			trigger = trigger,
			result = result,
			durationMs = elapsed(operation.startedAtMs),
			qaFaultCorrelation =
				qaFaultCorrelation ?: operation.qaFaultCorrelation
		)
	)

	fun preparation(
		operation: ReaderPageDiagnosticOperation,
		state: ReaderPagePreparationDiagnosticState,
		reason: ReaderPageRasterDeferralReason? = null,
		eventVersion: Long? = null,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	) = publish(
		ReaderPageDiagnostic.preparation(
			readerSession = readerSession,
			attempt = operation.attempt,
			rasterGeneration = operation.rasterGeneration,
			state = state,
			reason = reason,
			eventVersion = eventVersion,
			durationMs = elapsed(operation.startedAtMs),
			qaFaultCorrelation =
				qaFaultCorrelation ?: operation.qaFaultCorrelation
		)
	)

	fun repair(
		operation: ReaderPageDiagnosticOperation,
		state: ReaderPageRepairDiagnosticState,
		reason: ReaderPageRasterDeferralReason? = null,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	) = publish(
		ReaderPageDiagnostic.repair(
			readerSession = readerSession,
			attempt = operation.attempt,
			rasterGeneration = operation.rasterGeneration,
			centerOrdinal = operation.ordinal,
			state = state,
			reason = reason,
			durationMs = elapsed(operation.startedAtMs),
			qaFaultCorrelation =
				qaFaultCorrelation ?: operation.qaFaultCorrelation
		)
	)

	fun deck(
		generation: Long,
		repairAttempt: Long?,
		role: ReaderDeckSubmissionRole,
		prepared: Boolean,
		active: Long?,
		pending: Long?,
		startedAtMs: Long,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	) = publish(
		ReaderPageDiagnostic.deck(
			readerSession = readerSession,
			generation = generation,
			repairAttempt = repairAttempt,
			role = role,
			prepared = prepared,
			active = active,
			pending = pending,
			durationMs = elapsed(startedAtMs),
			qaFaultCorrelation = qaFaultCorrelation
		)
	)

	fun relocation(
		request: paige.navic.reader.ReaderPageRelocationRequest,
		state: ReaderPageRelocationDiagnosticState,
		queueDepth: Int,
		startedAtMs: Long,
		rejectionReason: ReaderPageRelocationDiagnosticRejectionReason =
			ReaderPageRelocationDiagnosticRejectionReason.None,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	) = publish(
		ReaderPageDiagnostic.relocation(
			readerSession = readerSession,
			token = request.token.value,
			gestureId = request.gestureId,
			source = request.sourceOrdinal,
			target = request.destinationOrdinal,
			logicalDirection = request.logicalDirection,
			rasterGeneration = request.rasterGeneration,
			textureGeneration = request.textureGeneration,
			state = state,
			rejectionReason = rejectionReason,
			queueDepth = queueDepth,
			durationMs = elapsed(startedAtMs),
			qaFaultCorrelation = qaFaultCorrelation
		)
	)

	fun handoff(
		handoffAttemptId: Long,
		token: String,
		target: Int,
		visualState: Boolean,
		nextFrame: Boolean,
		result: ReaderPageHandoffDiagnosticResult,
		startedAtMs: Long,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	) = publish(
		ReaderPageDiagnostic.handoff(
			readerSession = readerSession,
			handoffAttemptId = handoffAttemptId,
			token = token,
			target = target,
			visualState = visualState,
			nextFrame = nextFrame,
			result = result,
			durationMs = elapsed(startedAtMs),
			qaFaultCorrelation = qaFaultCorrelation
		)
	)

	fun prefetch(
		prefetchSession: Long,
		rasterEpoch: Long,
		state: ReaderPagePrefetchDiagnosticState,
		targetCount: Int,
		startedAtMs: Long
	) = publish(
		ReaderPageDiagnostic.prefetch(
			readerSession = readerSession,
			prefetchSession = prefetchSession,
			rasterEpoch = rasterEpoch,
			state = state,
			targetCount = targetCount,
			durationMs = elapsed(startedAtMs)
		)
	)

	fun now(): Long = nowMs()

	private fun elapsed(startedAtMs: Long): Long =
		(nowMs() - startedAtMs).coerceAtLeast(0L)

	private fun publish(message: String) {
		runCatching { emit(message) }
	}
}

internal class ReaderPageDeckDiagnosticTracker(
	private val diagnostics: ReaderPageRuntimeDiagnostics
) {
	private data class Submission(
		val repairAttempt: Long?,
		val role: ReaderDeckSubmissionRole,
		val startedAtMs: Long,
		val qaFaultCorrelation: ReaderPageQaFaultCorrelation?,
		var submitted: Boolean = false
	)

	private val submissions = mutableMapOf<Long, Submission>()

	fun begin(
		generation: Long,
		repairAttempt: Long?,
		role: ReaderDeckSubmissionRole,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	) {
		check(
			submissions.put(
				generation,
				Submission(
					repairAttempt = repairAttempt,
					role = role,
					startedAtMs = diagnostics.now(),
					qaFaultCorrelation = qaFaultCorrelation
				)
			) == null
		) { "Deck diagnostics already own generation $generation" }
	}

	fun submitted(
		generation: Long,
		active: Long?,
		pending: Long?
	): Boolean {
		val submission = submissions[generation]
			?.takeUnless { it.submitted }
			?: return false
		submission.submitted = true
		diagnostics.deck(
			generation = generation,
			repairAttempt = submission.repairAttempt,
			role = submission.role,
			prepared = false,
			active = active,
			pending = pending,
			startedAtMs = submission.startedAtMs,
			qaFaultCorrelation = submission.qaFaultCorrelation
		)
		return true
	}

	fun prepared(
		generation: Long,
		active: Long?,
		pending: Long?
	): Boolean {
		val submission = submissions.remove(generation) ?: return false
		diagnostics.deck(
			generation = generation,
			repairAttempt = submission.repairAttempt,
			role = submission.role,
			prepared = true,
			active = active,
			pending = pending,
			startedAtMs = submission.startedAtMs,
			qaFaultCorrelation = submission.qaFaultCorrelation
		)
		return true
	}

	fun cancel(generation: Long): Boolean = submissions.remove(generation) != null

	fun cancelAll() {
		submissions.clear()
	}

	fun pendingCount(): Int = submissions.size
}

internal object ReaderPageDiagnostic {
	private fun qaFaultCorrelationFields(
		correlation: ReaderPageQaFaultCorrelation?
	): String {
		val operation = correlation?.appliedOperation
		return "qaFaultRequestId=${correlation?.requestId ?: "none"} " +
			"qaFaultRelation=${correlation?.relation?.name ?: "None"} " +
			"qaFaultPublicationEpoch=${operation?.publicationEpoch ?: -1L} " +
			"qaFaultPersistenceAttemptId=${operation?.persistenceAttemptId ?: -1L} " +
			"qaFaultRasterRequestEpoch=${operation?.rasterRequestEpoch ?: -1L} " +
			"qaFaultRepairAttemptId=${operation?.repairAttemptId ?: -1L} " +
			"qaFaultPreparationAttemptId=${operation?.preparationAttemptId ?: -1L} " +
			"qaFaultRelocationToken=${operation?.relocationToken ?: "none"} " +
			"qaFaultHandoffAttemptId=${operation?.handoffAttemptId ?: -1L}"
	}

	fun qaFault(
		readerSession: Long,
		event: ReaderPageQaFaultEvent
	): String {
		val operation = event.operation
		return "reader-qa-fault session=$readerSession " +
			"requestId=${event.ticket.requestId} fault=${event.ticket.fault.name} " +
			"seam=${event.seam} state=${event.state.name} " +
			"publicationEpoch=${operation?.publicationEpoch ?: -1L} " +
			"persistenceAttemptId=${operation?.persistenceAttemptId ?: -1L} " +
			"rasterRequestEpoch=${operation?.rasterRequestEpoch ?: -1L} " +
			"repairAttemptId=${operation?.repairAttemptId ?: -1L} " +
			"preparationAttemptId=${operation?.preparationAttemptId ?: -1L} " +
			"relocationToken=${operation?.relocationToken ?: "none"} " +
			"handoffAttemptId=${operation?.handoffAttemptId ?: -1L} " +
			"releaseRequestId=${event.releaseRequestId ?: "none"} " +
			"result=${event.result}"
	}

	fun gesture(
		readerSession: Long,
		gestureId: Long,
		outcome: ReaderPageGestureTerminalOutcome,
		owner: ReaderPagePointerOwnership,
		rasterGeneration: Long,
		textureGeneration: Long,
		physicalDirection: ReaderPagePhysicalDirection?,
		logicalDirection: ReaderPageTurnDirection?,
		durationMs: Long
	): String =
		"reader-gesture session=$readerSession gestureId=$gestureId " +
			"outcome=$outcome owner=$owner rasterGeneration=$rasterGeneration " +
			"textureGeneration=$textureGeneration physicalDirection=$physicalDirection " +
			"logicalDirection=$logicalDirection durationMs=$durationMs"

	fun lifecycleCancellation(
		readerSession: Long,
		gestureId: Long,
		reason: ReaderPageLifecycleCancellationReason
	): String =
		"reader-lifecycle-cancellation session=$readerSession " +
			"gestureId=$gestureId reason=$reason"

	fun teardownFailure(
		readerSession: Long,
		failure: ReaderPageTeardownException
	): String =
		"reader-teardown-failure session=$readerSession " +
			"stage=${failure.stage} rendererStage=${failure.rendererStage} " +
			"suppressed=${failure.totalSuppressedFailureCount()}"

	fun publication(
		readerSession: Long,
		digestPrefix: String,
		rasterEpoch: Long,
		persistenceAttemptId: ReaderPagePersistenceAttemptId,
		result: ReaderPagePublicationDiagnosticResult,
		durationMs: Long,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	): String =
		"reader-raster-publication session=$readerSession " +
			"digestPrefix=$digestPrefix rasterEpoch=$rasterEpoch " +
			"persistenceAttemptId=${persistenceAttemptId.value} " +
			"result=$result durationMs=$durationMs " +
			qaFaultCorrelationFields(qaFaultCorrelation)

	fun rasterAcquisition(
		readerSession: Long,
		attempt: Long,
		rasterGeneration: Long,
		ordinal: Int,
		source: ReaderPageRasterAcquisitionSource,
		trigger: ReaderPageRasterAcquisitionTrigger,
		result: ReaderPageRasterAcquisitionResult,
		durationMs: Long,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	): String =
		"reader-raster-acquisition session=$readerSession attempt=$attempt " +
			"rasterGeneration=$rasterGeneration ordinal=$ordinal source=$source " +
			"trigger=$trigger result=$result durationMs=$durationMs " +
			qaFaultCorrelationFields(qaFaultCorrelation)

	fun preparation(
		readerSession: Long,
		attempt: Long,
		rasterGeneration: Long,
		state: ReaderPagePreparationDiagnosticState,
		reason: ReaderPageRasterDeferralReason?,
		eventVersion: Long?,
		durationMs: Long,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	): String =
		"reader-preparation session=$readerSession attempt=$attempt " +
			"rasterGeneration=$rasterGeneration state=$state " +
			"reason=${reason?.name ?: "None"} " +
			"eventVersion=${eventVersion ?: -1L} durationMs=$durationMs " +
			qaFaultCorrelationFields(qaFaultCorrelation)

	fun repair(
		readerSession: Long,
		attempt: Long,
		rasterGeneration: Long,
		centerOrdinal: Int,
		state: ReaderPageRepairDiagnosticState,
		reason: ReaderPageRasterDeferralReason?,
		durationMs: Long,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	): String =
		"reader-repair session=$readerSession attempt=$attempt " +
			"rasterGeneration=$rasterGeneration centerOrdinal=$centerOrdinal " +
			"state=$state reason=${reason?.name ?: "None"} durationMs=$durationMs " +
			qaFaultCorrelationFields(qaFaultCorrelation)

	fun deck(
		readerSession: Long,
		generation: Long,
		repairAttempt: Long?,
		role: ReaderDeckSubmissionRole,
		prepared: Boolean,
		active: Long?,
		pending: Long?,
		durationMs: Long,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	): String =
		"reader-deck session=$readerSession generation=$generation " +
			"repairAttempt=${repairAttempt ?: -1L} role=$role prepared=$prepared " +
			"active=$active pending=$pending durationMs=$durationMs " +
			qaFaultCorrelationFields(qaFaultCorrelation)

	fun relocation(
		readerSession: Long,
		token: String,
		gestureId: Long,
		source: Int,
		target: Int,
		logicalDirection: ReaderPageTurnDirection,
		rasterGeneration: Long,
		textureGeneration: Long,
		state: ReaderPageRelocationDiagnosticState,
		rejectionReason: ReaderPageRelocationDiagnosticRejectionReason =
			ReaderPageRelocationDiagnosticRejectionReason.None,
		queueDepth: Int,
		durationMs: Long,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	): String {
		require(
			(state == ReaderPageRelocationDiagnosticState.Rejected) ==
				(rejectionReason != ReaderPageRelocationDiagnosticRejectionReason.None)
		)
		return "reader-relocation session=$readerSession token=$token gestureId=$gestureId " +
			"source=$source target=$target logicalDirection=$logicalDirection " +
			"rasterGeneration=$rasterGeneration textureGeneration=$textureGeneration " +
			"state=$state rejectionReason=$rejectionReason " +
			"queueDepth=$queueDepth durationMs=$durationMs " +
			qaFaultCorrelationFields(qaFaultCorrelation)
	}

	fun handoff(
		readerSession: Long,
		token: String,
		handoffAttemptId: Long,
		target: Int,
		visualState: Boolean,
		nextFrame: Boolean,
		result: ReaderPageHandoffDiagnosticResult,
		durationMs: Long,
		qaFaultCorrelation: ReaderPageQaFaultCorrelation? = null
	): String = "reader-handoff session=$readerSession token=$token " +
		"handoffAttemptId=$handoffAttemptId target=$target " +
		"visualState=$visualState nextFrame=$nextFrame result=$result " +
		"durationMs=$durationMs ${qaFaultCorrelationFields(qaFaultCorrelation)}"

	fun residency(
		readerSession: Long,
		metrics: ReaderPlayLikeCurlRasterResidencyMetrics
	): String =
		"reader-residency session=$readerSession residents=${metrics.residentEntries} " +
			"residentLimit=${metrics.residentEntryLimit} " +
			"decodedUnique=${metrics.uniqueDecodedBitmaps} " +
			"decodedUniqueLimit=${metrics.uniqueDecodedBitmapLimit} " +
			"peakResidents=${metrics.peakResidentEntries} " +
			"peakDecodedUnique=${metrics.peakUniqueDecodedBitmaps} " +
			"pinned=${metrics.pinnedEntries} " +
			"pendingReleases=${metrics.pendingValueReleases} " +
			"evicted=${metrics.evictedEntries} released=${metrics.releasedEntries}"

	fun rasterCache(
		readerSession: Long,
		phase: ReaderPageOwnershipPhase,
		metrics: ReaderPageRasterCacheMetrics
	): String =
		"reader-raster-cache session=$readerSession phase=${phase.wireName} " +
			"diskEntries=${metrics.diskEntries} diskBytes=${metrics.diskBytes} " +
			"diskByteLimit=${metrics.diskByteLimit} " +
			"decodedEntries=${metrics.decodedEntries} " +
			"decodedUnique=${metrics.uniqueDecodedBitmaps} " +
			"decodedUniqueLimit=${metrics.uniqueDecodedBitmapLimit} " +
			"pendingDecodedReleases=${metrics.pendingDecodedReleases} " +
			"activeEncodePins=${metrics.activeEncodePins} " +
			"encodePinnedIdentities=${metrics.encodePinnedIdentities}"

	fun ownership(
		readerSession: Long,
		phase: ReaderPageOwnershipPhase,
		snapshot: ReaderPageOwnershipSnapshot
	): String =
		"reader-ownership session=$readerSession phase=${phase.wireName} " +
			"residents=${snapshot.adapterResidents} " +
			"residentLimit=${snapshot.bounds.adapterResidentLimit} " +
			"adapterDecoded=${snapshot.adapterDecodedBitmaps} " +
			"adapterDecodedLimit=${snapshot.bounds.adapterDecodedBitmapLimit} " +
			"cacheDecoded=${snapshot.cacheDecodedBitmaps} " +
			"cacheDecodedLimit=${snapshot.bounds.cacheDecodedBitmapLimit} " +
			"staged=${snapshot.stagedPublications} " +
			"stagedLimit=${snapshot.bounds.stagedPublicationLimit} " +
			"activeLeases=${snapshot.activeDeckLeases} " +
			"activeLeaseLimit=${snapshot.bounds.activeDeckLeaseLimit} " +
			"pendingLeases=${snapshot.pendingDeckLeases} " +
			"pendingLeaseLimit=${snapshot.bounds.pendingDeckLeaseLimit} " +
			"releaseInFlightLeases=${snapshot.releaseInFlightDeckLeases} " +
			"releaseInFlightLeaseLimit=${snapshot.bounds.releaseInFlightDeckLeaseLimit} " +
			"orphanLeases=${snapshot.orphanDeckLeases} " +
			"orphanLeaseLimit=${snapshot.bounds.orphanDeckLeaseLimit} " +
			"textures=${snapshot.rendererTextures} " +
			"textureLimit=${snapshot.bounds.rendererTextureLimit} " +
			"callbacks=${snapshot.pendingCallbacks} " +
			"callbackLimit=${snapshot.bounds.pendingCallbackLimit} " +
			"relocationReservations=${snapshot.relocationReservations} " +
			"queuedRelocations=${snapshot.queuedRelocations} " +
			"relocations=${snapshot.relocationTokens} " +
			"relocationLimit=${snapshot.bounds.relocationTokenLimit} " +
			"withinBounds=${snapshot.withinBounds()}"

	fun ownershipUnavailable(
		readerSession: Long,
		phase: ReaderPageOwnershipPhase,
		reason: ReaderPageOwnershipUnavailableReason
	): String {
		val status = when (reason) {
			ReaderPageOwnershipUnavailableReason.ApplicationEpochChanged ->
				"APPLICATION_EPOCH_CHANGED"
			is ReaderPageOwnershipUnavailableReason.Renderer -> {
				require(reason.status != PageSurfaceOwnershipResult.Status.AVAILABLE)
				reason.status.name
			}
		}
		return "reader-ownership-unavailable session=$readerSession " +
			"phase=${phase.wireName} status=$status"
	}

	fun prefetch(
		readerSession: Long,
		prefetchSession: Long,
		rasterEpoch: Long,
		state: ReaderPagePrefetchDiagnosticState,
		targetCount: Int,
		durationMs: Long
	): String =
		"reader-prefetch session=$readerSession prefetchSession=$prefetchSession " +
			"rasterEpoch=$rasterEpoch state=$state targetCount=$targetCount " +
			"durationMs=$durationMs"
}
