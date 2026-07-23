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
	Failed,
	Stale,
	Cancelled
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

internal enum class ReaderPageHandoffDiagnosticResult {
	Ready,
	Detached,
	TimedOut,
	Invalidated,
	CallbackCapacity,
	Cancelled
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
		ReaderWebViewVisualHandoffFailure.Cancelled ->
			ReaderPageHandoffDiagnosticResult.Cancelled
	}
}

internal enum class ReaderPagePrefetchDiagnosticState {
	Queued,
	Running,
	Completed,
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
	val startedAtMs: Long
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
		ordinal: Int
	): ReaderPageDiagnosticOperation = ReaderPageDiagnosticOperation(
		attempt = ReaderPageDiagnosticAttemptIds.incrementAndGet(),
		rasterGeneration = rasterGeneration,
		ordinal = ordinal,
		startedAtMs = nowMs()
	)

	fun publication(
		digest: String,
		rasterEpoch: Long,
		result: ReaderPagePublicationDiagnosticResult,
		startedAtMs: Long
	) = publish(
		ReaderPageDiagnostic.publication(
			readerSession = readerSession,
			digestPrefix = diagnosticDigestPrefix(digest),
			rasterEpoch = rasterEpoch,
			result = result,
			durationMs = elapsed(startedAtMs)
		)
	)

	fun rasterAcquisition(
		operation: ReaderPageDiagnosticOperation,
		source: ReaderPageRasterAcquisitionSource,
		trigger: ReaderPageRasterAcquisitionTrigger,
		result: ReaderPageRasterAcquisitionResult
	) = publish(
		ReaderPageDiagnostic.rasterAcquisition(
			readerSession = readerSession,
			attempt = operation.attempt,
			rasterGeneration = operation.rasterGeneration,
			ordinal = operation.ordinal,
			source = source,
			trigger = trigger,
			result = result,
			durationMs = elapsed(operation.startedAtMs)
		)
	)

	fun preparation(
		operation: ReaderPageDiagnosticOperation,
		state: ReaderPagePreparationDiagnosticState,
		reason: ReaderPageRasterDeferralReason? = null,
		eventVersion: Long? = null
	) = publish(
		ReaderPageDiagnostic.preparation(
			readerSession = readerSession,
			attempt = operation.attempt,
			rasterGeneration = operation.rasterGeneration,
			state = state,
			reason = reason,
			eventVersion = eventVersion,
			durationMs = elapsed(operation.startedAtMs)
		)
	)

	fun repair(
		operation: ReaderPageDiagnosticOperation,
		state: ReaderPageRepairDiagnosticState,
		reason: ReaderPageRasterDeferralReason? = null
	) = publish(
		ReaderPageDiagnostic.repair(
			readerSession = readerSession,
			attempt = operation.attempt,
			rasterGeneration = operation.rasterGeneration,
			centerOrdinal = operation.ordinal,
			state = state,
			reason = reason,
			durationMs = elapsed(operation.startedAtMs)
		)
	)

	fun deck(
		generation: Long,
		repairAttempt: Long?,
		role: ReaderDeckSubmissionRole,
		prepared: Boolean,
		active: Long?,
		pending: Long?,
		startedAtMs: Long
	) = publish(
		ReaderPageDiagnostic.deck(
			readerSession = readerSession,
			generation = generation,
			repairAttempt = repairAttempt,
			role = role,
			prepared = prepared,
			active = active,
			pending = pending,
			durationMs = elapsed(startedAtMs)
		)
	)

	fun relocation(
		request: paige.navic.reader.ReaderPageRelocationRequest,
		state: ReaderPageRelocationDiagnosticState,
		queueDepth: Int,
		startedAtMs: Long
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
			queueDepth = queueDepth,
			durationMs = elapsed(startedAtMs)
		)
	)

	fun handoff(
		handoffAttemptId: Long,
		token: String,
		target: Int,
		visualState: Boolean,
		nextFrame: Boolean,
		result: ReaderPageHandoffDiagnosticResult,
		startedAtMs: Long
	) = publish(
		ReaderPageDiagnostic.handoff(
			readerSession = readerSession,
			handoffAttemptId = handoffAttemptId,
			token = token,
			target = target,
			visualState = visualState,
			nextFrame = nextFrame,
			result = result,
			durationMs = elapsed(startedAtMs)
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
		var submitted: Boolean = false
	)

	private val submissions = mutableMapOf<Long, Submission>()

	fun begin(
		generation: Long,
		repairAttempt: Long?,
		role: ReaderDeckSubmissionRole
	) {
		check(
			submissions.put(
				generation,
				Submission(
					repairAttempt = repairAttempt,
					role = role,
					startedAtMs = diagnostics.now()
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
			startedAtMs = submission.startedAtMs
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
			startedAtMs = submission.startedAtMs
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
		result: ReaderPagePublicationDiagnosticResult,
		durationMs: Long
	): String =
		"reader-raster-publication session=$readerSession " +
			"digestPrefix=$digestPrefix rasterEpoch=$rasterEpoch " +
			"result=$result durationMs=$durationMs"

	fun rasterAcquisition(
		readerSession: Long,
		attempt: Long,
		rasterGeneration: Long,
		ordinal: Int,
		source: ReaderPageRasterAcquisitionSource,
		trigger: ReaderPageRasterAcquisitionTrigger,
		result: ReaderPageRasterAcquisitionResult,
		durationMs: Long
	): String =
		"reader-raster-acquisition session=$readerSession attempt=$attempt " +
			"rasterGeneration=$rasterGeneration ordinal=$ordinal source=$source " +
			"trigger=$trigger result=$result durationMs=$durationMs"

	fun preparation(
		readerSession: Long,
		attempt: Long,
		rasterGeneration: Long,
		state: ReaderPagePreparationDiagnosticState,
		reason: ReaderPageRasterDeferralReason?,
		eventVersion: Long?,
		durationMs: Long
	): String =
		"reader-preparation session=$readerSession attempt=$attempt " +
			"rasterGeneration=$rasterGeneration state=$state " +
			"reason=${reason?.name ?: "None"} " +
			"eventVersion=${eventVersion ?: -1L} durationMs=$durationMs"

	fun repair(
		readerSession: Long,
		attempt: Long,
		rasterGeneration: Long,
		centerOrdinal: Int,
		state: ReaderPageRepairDiagnosticState,
		reason: ReaderPageRasterDeferralReason?,
		durationMs: Long
	): String =
		"reader-repair session=$readerSession attempt=$attempt " +
			"rasterGeneration=$rasterGeneration centerOrdinal=$centerOrdinal " +
			"state=$state reason=${reason?.name ?: "None"} durationMs=$durationMs"

	fun deck(
		readerSession: Long,
		generation: Long,
		repairAttempt: Long?,
		role: ReaderDeckSubmissionRole,
		prepared: Boolean,
		active: Long?,
		pending: Long?,
		durationMs: Long
	): String =
		"reader-deck session=$readerSession generation=$generation " +
			"repairAttempt=${repairAttempt ?: -1L} role=$role prepared=$prepared " +
			"active=$active pending=$pending durationMs=$durationMs"

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
		queueDepth: Int,
		durationMs: Long
	): String =
		"reader-relocation session=$readerSession token=$token gestureId=$gestureId " +
			"source=$source target=$target logicalDirection=$logicalDirection " +
			"rasterGeneration=$rasterGeneration textureGeneration=$textureGeneration " +
			"state=$state queueDepth=$queueDepth durationMs=$durationMs"

	fun handoff(
		readerSession: Long,
		handoffAttemptId: Long,
		token: String,
		target: Int,
		visualState: Boolean,
		nextFrame: Boolean,
		result: ReaderPageHandoffDiagnosticResult,
		durationMs: Long
	): String =
		"reader-handoff session=$readerSession token=$token " +
			"handoffAttemptId=$handoffAttemptId target=$target " +
			"visualState=$visualState nextFrame=$nextFrame " +
			"result=$result durationMs=$durationMs"

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
