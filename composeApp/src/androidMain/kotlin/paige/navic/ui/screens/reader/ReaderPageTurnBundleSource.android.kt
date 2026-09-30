package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import karacken.curl.PageSurfaceView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import paige.navic.reader.ReaderPageAdjacentPrefetchPublicationAllowance
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageMaximumForegroundPublicationEntries
import paige.navic.reader.ReaderPageMaximumPublicationCallbacks
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderPageRelocationRequest
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPixelRect
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.readerPageRasterStorageRoot
import paige.navic.util.core.Logger
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

private const val ReaderPageTurnBundleSourceTag = "ReaderPageTurnBundleSource"
private const val MaxCachedSnapshots = 5
private val ReaderPageTurnBundleInventorySources = listOf(
	ReaderLegacyInventorySource.RasterSnapshotCache,
	ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback,
	ReaderLegacyInventorySource.RasterHydration,
	ReaderLegacyInventorySource.RasterPublication,
	ReaderLegacyInventorySource.RasterGenerationAndPersistence,
	ReaderLegacyInventorySource.RasterCaptureAndVisualState,
	ReaderLegacyInventorySource.RasterLiveValidation,
	ReaderLegacyInventorySource.RasterStoreAndCache
)

private fun readerPassiveRasterSnapshot(
	pageIndex: Int,
	kind: ReaderPageTurnTransitionKind,
	bitmap: Bitmap,
	reference: ReaderPageSlideSnapshot,
	captureGeometry: ReaderPassiveRasterGeometry,
	bitmapQuality: ReaderPageBitmapQuality,
	captureStartedAtMillis: Long = SystemClock.uptimeMillis()
): ReaderPageSlideSnapshot? {
	val surfaceWidth = reference.surfaceRectInWindow.width()
	val surfaceHeight = reference.surfaceRectInWindow.height()
	val sourceLeft = reference.surfaceRectInWindow.left - captureGeometry.captureLeft
	val sourceTop = reference.surfaceRectInWindow.top - captureGeometry.captureTop
	val sourceRight = sourceLeft + surfaceWidth
	val sourceBottom = sourceTop + surfaceHeight
	val expectedBitmapWidth = readerPageTurnAnimationBitmapDimension(
		surfaceWidth,
		bitmapQuality
	)
	val expectedBitmapHeight = readerPageTurnAnimationBitmapDimension(
		surfaceHeight,
		bitmapQuality
	)
	if (
		bitmap.isRecycled ||
		bitmap.width != captureGeometry.captureWidth ||
		bitmap.height != captureGeometry.captureHeight ||
		reference.bitmap.isRecycled ||
		reference.key.bitmapQuality != bitmapQuality ||
		reference.bitmap.width != expectedBitmapWidth ||
		reference.bitmap.height != expectedBitmapHeight ||
		reference.key.bitmapWidth != expectedBitmapWidth ||
		reference.key.bitmapHeight != expectedBitmapHeight ||
		reference.key.surfaceWidth != surfaceWidth ||
		reference.key.surfaceHeight != surfaceHeight ||
		sourceLeft < 0 ||
		sourceTop < 0 ||
		sourceRight > bitmap.width ||
		sourceBottom > bitmap.height
	) return null
	val surfaceBitmap = if (
		sourceLeft == 0 && sourceTop == 0 &&
			sourceRight == bitmap.width && sourceBottom == bitmap.height
	) {
		bitmap
	} else {
		runCatching {
			Bitmap.createBitmap(
				bitmap,
				sourceLeft,
				sourceTop,
				surfaceWidth,
				surfaceHeight
			)
		}.getOrNull()?.also { cropped ->
			if (cropped !== bitmap && !bitmap.isRecycled) bitmap.recycle()
		}
	} ?: return null
	val qualityBitmap = if (
		surfaceBitmap.width == expectedBitmapWidth &&
			surfaceBitmap.height == expectedBitmapHeight
	) {
		surfaceBitmap
	} else {
		runCatching {
			Bitmap.createScaledBitmap(
				surfaceBitmap,
				expectedBitmapWidth,
				expectedBitmapHeight,
				true
			)
		}.getOrNull()?.also { scaled ->
			if (scaled !== surfaceBitmap && !surfaceBitmap.isRecycled) {
				surfaceBitmap.recycle()
			}
		}
	} ?: return null
	return ReaderPageSlideSnapshot(
		key = ReaderPageSlideSnapshotKey(
			visualPageIndex = pageIndex,
			kind = kind,
			bitmapQuality = bitmapQuality,
			bitmapWidth = qualityBitmap.width,
			bitmapHeight = qualityBitmap.height,
			surfaceWidth = reference.surfaceRectInWindow.width(),
			surfaceHeight = reference.surfaceRectInWindow.height()
		),
		bitmap = qualityBitmap,
		surfaceRectInWindow = Rect(reference.surfaceRectInWindow),
		leafGeometry = reference.leafGeometry,
		reverseFaceColor = reference.reverseFaceColor,
		captureMillis = (SystemClock.uptimeMillis() - captureStartedAtMillis).coerceAtLeast(0L)
	)
}

private const val MaxCachedRasterDescriptors = 32
private const val LiveValidationStripRows = 64
private const val LiveValidationAbsoluteMeanDistance = 8.0
private const val LiveValidationAbsoluteRmsDistance = 18.0
private const val LiveValidationAbsoluteCloseRatio = 0.96
private const val LiveValidationAbsoluteFarPixelLimit = 4L
private const val LiveValidationRelativeDistanceRatio = 0.72
private const val LiveValidationEquivalentMaximumDistance = 8
private const val LiveValidationCloseDistance = 24
private const val LiveValidationFarDistance = 48

internal interface ReaderPageRasterPixels {
	val width: Int
	val height: Int
	fun readRows(top: Int, rowCount: Int, destination: IntArray)
}

internal data class ReaderPageRasterDistance(
	val pixelCount: Long,
	val mean: Double,
	val rms: Double,
	val closeRatio: Double,
	val roundTripCloseCreditRatio: Double,
	val effectiveCloseRatio: Double,
	val farPixelCount: Long,
	val maximumDistance: Int,
	val firstNonOpaquePixelCount: Long,
	val secondNonOpaquePixelCount: Long
)

private fun readerPageRasterColorDistance(firstColor: Int, secondColor: Int): Int = maxOf(
	abs((firstColor ushr 16 and 0xff) - (secondColor ushr 16 and 0xff)),
	abs((firstColor ushr 8 and 0xff) - (secondColor ushr 8 and 0xff)),
	abs((firstColor and 0xff) - (secondColor and 0xff))
)

private class ReaderPageRasterDistanceAccumulator {
	private var pixelCount = 0L
	private var distanceSum = 0L
	private var squaredDistanceSum = 0L
	private var closePixelCount = 0L
	private var roundTripCloseCreditCount = 0L
	private var farPixelCount = 0L
	private var maximumDistance = 0
	private var firstNonOpaquePixelCount = 0L
	private var secondNonOpaquePixelCount = 0L

	fun add(firstColor: Int, secondColor: Int, roundTripColor: Int? = null) {
		val distance = readerPageRasterColorDistance(firstColor, secondColor)
		val roundTripDistance = roundTripColor?.let {
			readerPageRasterColorDistance(firstColor, it)
		}
		pixelCount += 1L
		distanceSum += distance
		squaredDistanceSum += distance.toLong() * distance
		if (distance <= LiveValidationCloseDistance) closePixelCount += 1L
		if (
			distance > LiveValidationCloseDistance &&
			roundTripDistance != null &&
			roundTripDistance <= LiveValidationCloseDistance &&
			roundTripDistance + LiveValidationCloseDistance < distance
		) {
			roundTripCloseCreditCount += 1L
		}
		if (distance > LiveValidationFarDistance) farPixelCount += 1L
		if (distance > maximumDistance) maximumDistance = distance
		if (firstColor ushr 24 != 0xff) firstNonOpaquePixelCount += 1L
		if (secondColor ushr 24 != 0xff) secondNonOpaquePixelCount += 1L
	}

	fun result(): ReaderPageRasterDistance? {
		if (pixelCount == 0L) return null
		return ReaderPageRasterDistance(
			pixelCount = pixelCount,
			mean = distanceSum.toDouble() / pixelCount,
			rms = sqrt(squaredDistanceSum.toDouble() / pixelCount),
			closeRatio = closePixelCount.toDouble() / pixelCount,
			roundTripCloseCreditRatio = roundTripCloseCreditCount.toDouble() / pixelCount,
			effectiveCloseRatio =
				(closePixelCount + roundTripCloseCreditCount).toDouble() / pixelCount,
			farPixelCount = farPixelCount,
			maximumDistance = maximumDistance,
			firstNonOpaquePixelCount = firstNonOpaquePixelCount,
			secondNonOpaquePixelCount = secondNonOpaquePixelCount
		)
	}
}

internal data class ReaderPageLiveRasterDistances(
	val target: ReaderPageRasterDistance,
	val sourceTarget: ReaderPageRasterDistance?,
	val source: ReaderPageRasterDistance?
)

private fun readerPageExactTwoTimesRoundTripColor(
	targetPixels: IntArray,
	width: Int,
	height: Int,
	haloTop: Int,
	x: Int,
	y: Int
): Int {
	var red = 0
	var green = 0
	var blue = 0
	for (yOffset in -1..1) {
		val sourceY = (y + yOffset).coerceIn(0, height - 1)
		val yWeight = if (yOffset == 0) 6 else 1
		for (xOffset in -1..1) {
			val sourceX = (x + xOffset).coerceIn(0, width - 1)
			val xWeight = if (xOffset == 0) 6 else 1
			val weight = xWeight * yWeight
			val color = targetPixels[(sourceY - haloTop) * width + sourceX]
			red += (color ushr 16 and 0xff) * weight
			green += (color ushr 8 and 0xff) * weight
			blue += (color and 0xff) * weight
		}
	}
	return 0xff000000.toInt() or
		(((red + 32) / 64) shl 16) or
		(((green + 32) / 64) shl 8) or
		((blue + 32) / 64)
}

internal fun readerPageLiveRasterDistances(
	candidate: ReaderPageRasterPixels,
	expectedTarget: ReaderPageRasterPixels,
	expectedSource: ReaderPageRasterPixels?,
	exactTwoTimesRoundTrip: Boolean = false,
	cancellationCheck: () -> Unit
): ReaderPageLiveRasterDistances? {
	if (
		candidate.width <= 0 ||
		candidate.height <= 0 ||
		candidate.width != expectedTarget.width ||
		candidate.height != expectedTarget.height ||
		(
			expectedSource != null &&
			(
				expectedSource.width != candidate.width ||
				expectedSource.height != candidate.height
			)
		)
	) {
		return null
	}
	val rowCapacity = minOf(LiveValidationStripRows, candidate.height)
	val stripPixelCapacity = candidate.width.toLong() * rowCapacity
	val targetRowCapacity = rowCapacity + if (exactTwoTimesRoundTrip) 2 else 0
	val targetPixelCapacity = candidate.width.toLong() * targetRowCapacity
	if (
		stripPixelCapacity <= 0L ||
		stripPixelCapacity > Int.MAX_VALUE ||
		targetPixelCapacity <= 0L ||
		targetPixelCapacity > Int.MAX_VALUE
	) {
		return null
	}
	val candidatePixels = IntArray(stripPixelCapacity.toInt())
	val targetPixels = IntArray(targetPixelCapacity.toInt())
	val sourcePixels = expectedSource?.let { IntArray(stripPixelCapacity.toInt()) }
	val targetDistance = ReaderPageRasterDistanceAccumulator()
	val sourceTargetDistance = expectedSource?.let { ReaderPageRasterDistanceAccumulator() }
	val sourceDistance = expectedSource?.let { ReaderPageRasterDistanceAccumulator() }
	var top = 0
	while (top < candidate.height) {
		cancellationCheck()
		val rowCount = minOf(rowCapacity, candidate.height - top)
		val pixelCount = candidate.width * rowCount
		candidate.readRows(top, rowCount, candidatePixels)
		cancellationCheck()
		val targetHaloTop = if (exactTwoTimesRoundTrip) maxOf(0, top - 1) else top
		val targetHaloBottom = if (exactTwoTimesRoundTrip) {
			minOf(candidate.height, top + rowCount + 1)
		} else {
			top + rowCount
		}
		expectedTarget.readRows(
			targetHaloTop,
			targetHaloBottom - targetHaloTop,
			targetPixels
		)
		if (expectedSource != null && sourcePixels != null) {
			cancellationCheck()
			expectedSource.readRows(top, rowCount, sourcePixels)
		}
		for (index in 0 until pixelCount) {
			val localY = index / candidate.width
			val x = index - localY * candidate.width
			val globalY = top + localY
			val targetIndex = (globalY - targetHaloTop) * candidate.width + x
			val candidateColor = candidatePixels[index]
			val targetColor = targetPixels[targetIndex]
			val roundTripColor = if (
				exactTwoTimesRoundTrip &&
				readerPageRasterColorDistance(candidateColor, targetColor) >
					LiveValidationCloseDistance
			) {
				readerPageExactTwoTimesRoundTripColor(
					targetPixels = targetPixels,
					width = candidate.width,
					height = candidate.height,
					haloTop = targetHaloTop,
					x = x,
					y = globalY
				)
			} else {
				null
			}
			targetDistance.add(candidateColor, targetColor, roundTripColor)
			if (sourcePixels != null) {
				sourceTargetDistance?.add(sourcePixels[index], targetColor)
				sourceDistance?.add(candidateColor, sourcePixels[index])
			}
		}
		top += rowCount
	}
	return ReaderPageLiveRasterDistances(
		target = targetDistance.result() ?: return null,
		sourceTarget = sourceTargetDistance?.result(),
		source = sourceDistance?.result()
	)
}

private fun ReaderPageRasterDistance?.privacySafeMetrics(prefix: String): String {
	if (this == null) return "${prefix}Available=false"
	return "${prefix}Available=true " +
		"${prefix}MeanMilli=${(mean * 1_000.0).roundToLong()} " +
		"${prefix}RmsMilli=${(rms * 1_000.0).roundToLong()} " +
		"${prefix}ClosePermille=${(closeRatio * 1_000.0).roundToLong()} " +
		"${prefix}RoundTripCreditPermille=${
			(roundTripCloseCreditRatio * 1_000.0).roundToLong()
		} " +
		"${prefix}EffectiveClosePermille=${
			(effectiveCloseRatio * 1_000.0).roundToLong()
		} " +
		"${prefix}FarPixels=$farPixelCount " +
		"${prefix}MaximumDistance=$maximumDistance " +
		"${prefix}FirstNonOpaquePixels=$firstNonOpaquePixelCount " +
		"${prefix}SecondNonOpaquePixels=$secondNonOpaquePixelCount"
}

private fun ReaderPageTurnLiveCaptureDiagnostics?.privacySafeMetrics(): String {
	if (this == null) return "metricsAvailable=false"
	return "metricsAvailable=true " +
		"bitmapWidth=$bitmapWidth bitmapHeight=$bitmapHeight " +
		"rendererWidth=$rendererWidth rendererHeight=$rendererHeight " +
		"bufferWidth=$bufferWidth bufferHeight=$bufferHeight " +
		"cropLeft=$cropLeft cropTop=$cropTop cropWidth=$cropWidth cropHeight=$cropHeight " +
		"alphaSampledPixels=$alphaSampledPixels alphaNonOpaquePixels=$alphaNonOpaquePixels"
}

internal fun readerPageLiveRasterUsesExactTwoTimesRoundTrip(
	diagnostics: ReaderPageTurnLiveCaptureDiagnostics?
): Boolean = diagnostics != null &&
	diagnostics.bitmapWidth > 0 &&
	diagnostics.bitmapHeight > 0 &&
	diagnostics.alphaSampledPixels > 0 &&
	diagnostics.alphaNonOpaquePixels == 0 &&
	diagnostics.cropWidth.toLong() == diagnostics.bitmapWidth.toLong() * 2L &&
	diagnostics.cropHeight.toLong() == diagnostics.bitmapHeight.toLong() * 2L

private fun ReaderPageRasterDistance.isAggregateTargetMatch(): Boolean =
	mean <= LiveValidationAbsoluteMeanDistance &&
		rms <= LiveValidationAbsoluteRmsDistance &&
		effectiveCloseRatio >= LiveValidationAbsoluteCloseRatio

private fun ReaderPageRasterDistance.hasAbsoluteFarPixelAllowance(): Boolean =
	farPixelCount <= LiveValidationAbsoluteFarPixelLimit

private fun ReaderPageRasterDistance.isAuthoredEquivalent(): Boolean =
	mean <= 2.0 &&
		rms <= 4.0 &&
		maximumDistance <= LiveValidationEquivalentMaximumDistance

internal fun readerPageLiveRasterMatchesExpected(
	candidate: ReaderPageRasterPixels,
	expectedTarget: ReaderPageRasterPixels,
	expectedSource: ReaderPageRasterPixels?,
	exactTwoTimesRoundTrip: Boolean = false,
	cancellationCheck: () -> Unit = {}
): Boolean {
	fun rejected(reason: String): Boolean {
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Live page-turn raster match result=Rejected reason=$reason"
		)
		return false
	}
	val distances = readerPageLiveRasterDistances(
		candidate = candidate,
		expectedTarget = expectedTarget,
		expectedSource = expectedSource,
		exactTwoTimesRoundTrip = exactTwoTimesRoundTrip,
		cancellationCheck = cancellationCheck
	) ?: return rejected("Dimensions")
	val targetDistance = distances.target
	if (exactTwoTimesRoundTrip && targetDistance.roundTripCloseCreditRatio > 0.0) {
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Live page-turn raster normalization policy=ExactTwoTimesRoundTrip " +
				targetDistance.privacySafeMetrics("target")
		)
	}
	if (!targetDistance.isAggregateTargetMatch()) {
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Live page-turn raster metrics candidateWidth=${candidate.width} " +
				"candidateHeight=${candidate.height} " +
				targetDistance.privacySafeMetrics("target") + " " +
				distances.source.privacySafeMetrics("source") + " " +
				distances.sourceTarget.privacySafeMetrics("sourceTarget")
		)
		return rejected("TargetAggregate")
	}
	val sourceTargetDistance = distances.sourceTarget
		?: return if (targetDistance.hasAbsoluteFarPixelAllowance()) {
			true
		} else {
			rejected("AbsoluteFarPixelLimit")
		}
	if (sourceTargetDistance.isAuthoredEquivalent()) {
		return if (targetDistance.hasAbsoluteFarPixelAllowance()) {
			true
		} else {
			rejected("EquivalentFarPixelLimit")
		}
	}
	val sourceDistance = distances.source ?: return rejected("SourceDistanceUnavailable")
	if (targetDistance.farPixelCount != sourceDistance.farPixelCount) {
		return if (targetDistance.farPixelCount < sourceDistance.farPixelCount) {
			true
		} else {
			rejected("SourceFarPixelPreference")
		}
	}
	if (targetDistance.maximumDistance != sourceDistance.maximumDistance) {
		return if (targetDistance.maximumDistance < sourceDistance.maximumDistance) {
			true
		} else {
			rejected("SourceMaximumDistancePreference")
		}
	}
	return if (
		targetDistance.rms <=
			sourceDistance.rms * LiveValidationRelativeDistanceRatio + 1.5 &&
		targetDistance.mean <=
			sourceDistance.mean * LiveValidationRelativeDistanceRatio + 0.75
	) {
		true
	} else {
		rejected("SourceRelativeDistancePreference")
	}
}

internal fun <T : ReaderPageRasterPixels> readerPageSemanticLiveValidationResult(
	candidate: T?,
	expectedTarget: ReaderPageRasterPixels,
	expectedSource: ReaderPageRasterPixels?,
	isStillCurrent: Boolean,
	releaseCandidate: (T) -> Unit
): ReaderPageRelocationContentValidationResult {
	return try {
		when {
			!isStillCurrent -> ReaderPageRelocationContentValidationResult.Invalidated
			candidate == null -> ReaderPageRelocationContentValidationResult.ContentRejected
			readerPageLiveRasterMatchesExpected(candidate, expectedTarget, expectedSource) ->
				ReaderPageRelocationContentValidationResult.Accepted
			else -> ReaderPageRelocationContentValidationResult.ContentRejected
		}
	} finally {
		candidate?.let(releaseCandidate)
	}
}

internal fun readerPageLiveValidationIsCurrent(
	expectedGeneration: Long,
	currentGeneration: Long,
	closed: Boolean,
	callerCurrent: Boolean
): Boolean =
	!closed &&
		expectedGeneration == currentGeneration &&
		callerCurrent

internal fun readerPageLiveValidationReceiptFencedResult(
	workerResult: ReaderPageRelocationContentValidationResult,
	target: ReaderPageTurnPresentationTarget.Live,
	acceptedReceipt: ReaderPageTurnPresentationReceipt,
	currentReceipt: ReaderPageTurnPresentationReceipt?,
	isStillCurrent: Boolean
): ReaderPageRelocationContentValidationResult = when {
	workerResult != ReaderPageRelocationContentValidationResult.Accepted -> workerResult
	!isStillCurrent ||
		!readerPageTurnPresentationReceiptAccepted(
			target = target,
			initialReceipt = acceptedReceipt,
			finalReceipt = currentReceipt,
			foregroundSuccess = true
		) -> ReaderPageRelocationContentValidationResult.Invalidated
	else -> ReaderPageRelocationContentValidationResult.Accepted
}

internal data class ReaderPageLiveValidationWork<T : Any, C : Any>(
	val expectedTarget: T,
	val expectedSource: T?,
	val candidate: C
)

internal class ReaderPageLiveValidationSnapshotOwnership<T : Any, C : Any>(
	expectedTarget: T,
	expectedSource: T?,
	private val releaseExpected: (T) -> Unit,
	private val releaseCandidate: (C) -> Unit,
	private val onTerminal: () -> Unit = {}
) : ReaderPageRelocationContentValidationHandle {
	private enum class State {
		Capturing,
		Working,
		AwaitingMain,
		Cancelling,
		Terminal
	}

	private data class Owned<T : Any, C : Any>(
		val expectedTarget: T,
		val expectedSource: T?,
		val candidate: C?
	)

	private data class Publication<T : Any, C : Any>(
		val owned: Owned<T, C>,
		val result: ReaderPageRelocationContentValidationResult
	)

	private val lock = Any()
	private var state = State.Capturing
	private var expectedTarget: T? = expectedTarget
	private var expectedSource: T? = expectedSource
	private var candidate: C? = null
	private var captureHandle: ReaderPageRelocationContentValidationHandle? = null
	private var workerHandle: ReaderPageRelocationContentValidationHandle? = null
	private var finalFenceHandle: ReaderPageRelocationContentValidationHandle? = null
	private var workerActive = false
	private var workerResult: ReaderPageRelocationContentValidationResult? = null

	fun attachCapture(handle: ReaderPageRelocationContentValidationHandle) {
		val cancelImmediately = synchronized(lock) {
			if (state == State.Capturing) {
				check(captureHandle == null) { "Live validation capture handle already attached" }
				captureHandle = handle
				false
			} else {
				true
			}
		}
		if (cancelImmediately) handle.cancel()
	}

	fun beginWorker(candidate: C): ReaderPageLiveValidationWork<T, C>? {
		val work = synchronized(lock) {
			if (state != State.Capturing) return@synchronized null
			state = State.Working
			captureHandle = null
			workerActive = true
			this.candidate = candidate
			ReaderPageLiveValidationWork(
				expectedTarget = checkNotNull(expectedTarget),
				expectedSource = expectedSource,
				candidate = candidate
			)
		}
		if (work == null) releaseCandidate(candidate)
		return work
	}

	fun completeCapture(
		candidate: C?,
		result: ReaderPageRelocationContentValidationResult
	): Boolean {
		val accepted = synchronized(lock) {
			if (state != State.Capturing) return@synchronized false
			state = State.AwaitingMain
			captureHandle = null
			this.candidate = candidate
			workerResult = result
			true
		}
		if (!accepted) candidate?.let(releaseCandidate)
		return accepted
	}

	fun attachWorker(handle: ReaderPageRelocationContentValidationHandle) {
		val cancelImmediately = synchronized(lock) {
			if (state == State.Working && workerActive) {
				check(workerHandle == null) { "Live validation worker handle already attached" }
				workerHandle = handle
				false
			} else {
				true
			}
		}
		if (cancelImmediately) handle.cancel()
	}

	fun recordWorkerResult(result: ReaderPageRelocationContentValidationResult): Boolean =
		synchronized(lock) {
			if (
				state != State.Working ||
				!workerActive ||
				workerResult != null
			) {
				return@synchronized false
			}
			workerResult = result
			true
		}

	fun workerFinished(cancelled: Boolean): Boolean {
		var released: Owned<T, C>? = null
		val shouldPost = synchronized(lock) {
			if (!workerActive) return@synchronized false
			workerActive = false
			workerHandle = null
			when {
				state == State.Cancelling -> {
					state = State.Terminal
					released = takeOwnedLocked()
					false
				}
				state != State.Working -> false
				cancelled || workerResult == null -> {
					state = State.Terminal
					released = takeOwnedLocked()
					false
				}
				else -> {
					state = State.AwaitingMain
					true
				}
			}
		}
		released?.let(::releaseOwned)
		return shouldPost
	}

	fun attachFinalFence(handle: ReaderPageRelocationContentValidationHandle) {
		val cancelImmediately = synchronized(lock) {
			if (state == State.AwaitingMain) {
				check(finalFenceHandle == null) { "Live validation final fence already attached" }
				finalFenceHandle = handle
				false
			} else {
				true
			}
		}
		if (cancelImmediately) handle.cancel()
	}

	fun awaitingResult(): ReaderPageRelocationContentValidationResult? = synchronized(lock) {
		workerResult.takeIf { state == State.AwaitingMain }
	}

	fun publish(
		action: (T, T?, C?, ReaderPageRelocationContentValidationResult) -> Unit
	): Boolean {
		val publication = synchronized(lock) {
			if (state != State.AwaitingMain) return false
			state = State.Terminal
			finalFenceHandle = null
			val result = checkNotNull(workerResult)
			Publication(
				owned = takeOwnedLocked(),
				result = result
			)
		}
		try {
			action(
				publication.owned.expectedTarget,
				publication.owned.expectedSource,
				publication.owned.candidate,
				publication.result
			)
		} finally {
			releaseOwned(publication.owned)
		}
		return true
	}

	override fun cancel(): Boolean {
		var released: Owned<T, C>? = null
		var capture: ReaderPageRelocationContentValidationHandle? = null
		var worker: ReaderPageRelocationContentValidationHandle? = null
		var finalFence: ReaderPageRelocationContentValidationHandle? = null
		val cancelled = synchronized(lock) {
			when (state) {
				State.Capturing -> {
					state = State.Terminal
					capture = captureHandle
					captureHandle = null
					released = takeOwnedLocked()
					true
				}
				State.Working -> {
					state = State.Cancelling
					worker = workerHandle
					workerHandle = null
					true
				}
				State.AwaitingMain -> {
					state = State.Terminal
					finalFence = finalFenceHandle
					finalFenceHandle = null
					released = takeOwnedLocked()
					true
				}
				State.Cancelling,
				State.Terminal -> false
			}
		}
		if (!cancelled) return false
		try {
			capture?.cancel()
			worker?.cancel()
			finalFence?.cancel()
		} finally {
			released?.let(::releaseOwned)
		}
		return true
	}

	private fun takeOwnedLocked(): Owned<T, C> {
		val owned = Owned(
			expectedTarget = checkNotNull(expectedTarget),
			expectedSource = expectedSource,
			candidate = candidate
		)
		expectedTarget = null
		expectedSource = null
		candidate = null
		workerResult = null
		return owned
	}

	private fun releaseOwned(owned: Owned<T, C>) {
		try {
			releaseExpected(owned.expectedTarget)
		} finally {
			try {
				owned.expectedSource?.let(releaseExpected)
			} finally {
				try {
					owned.candidate?.let(releaseCandidate)
				} finally {
					onTerminal()
				}
			}
		}
	}
}

internal fun readerPageRasterGeometryMatches(
	kind: ReaderPageTurnTransitionKind,
	geometry: ReaderPageTurnLeafGeometry?
): Boolean = when (kind) {
	ReaderPageTurnTransitionKind.PortraitSlide -> geometry?.fullLeafRect != null
	ReaderPageTurnTransitionKind.LandscapeSpreadSlide ->
		geometry?.leftLeafRect != null && geometry.rightLeafRect != null
}

private data class ReaderPageRasterPhysicalLayout(
	val surfaceRectInWindow: ReaderPageTurnPixelRect,
	val fullLeafRectInWindow: ReaderPageTurnPixelRect?,
	val leftLeafRectInWindow: ReaderPageTurnPixelRect?,
	val gutterRectInWindow: ReaderPageTurnPixelRect?,
	val rightLeafRectInWindow: ReaderPageTurnPixelRect?
)

private data class ReaderPageRasterPhysicalLayoutAuthority(
	val kind: ReaderPageTurnTransitionKind,
	val layout: ReaderPageRasterPhysicalLayout,
	val epoch: Long
)

private fun readerPageRasterPhysicalLayout(
	surfaceRectInWindow: Rect,
	bitmapWidth: Int,
	bitmapHeight: Int,
	geometry: ReaderPageTurnLeafGeometry
): ReaderPageRasterPhysicalLayout? {
	val surfaceWidth = surfaceRectInWindow.width()
	val surfaceHeight = surfaceRectInWindow.height()
	if (
		bitmapWidth <= 0 ||
		bitmapHeight <= 0 ||
		surfaceWidth <= 0 ||
		surfaceHeight <= 0
	) {
		return null
	}

	fun scaleBoundary(value: Int, sourceExtent: Int, targetExtent: Int): Int =
		((value.toLong() * targetExtent + sourceExtent / 2L) / sourceExtent).toInt()

	fun mapToSurface(
		rect: ReaderPageTurnPixelRect?,
		allowZeroWidth: Boolean
	): ReaderPageTurnPixelRect? {
		if (rect == null) return null
		val validWidth = if (allowZeroWidth) rect.right >= rect.left else rect.right > rect.left
		if (
			rect.left < 0 ||
			rect.top < 0 ||
			rect.right > bitmapWidth ||
			rect.bottom > bitmapHeight ||
			!validWidth ||
			rect.bottom <= rect.top
		) {
			return null
		}
		return ReaderPageTurnPixelRect(
			left = surfaceRectInWindow.left + scaleBoundary(rect.left, bitmapWidth, surfaceWidth),
			top = surfaceRectInWindow.top + scaleBoundary(rect.top, bitmapHeight, surfaceHeight),
			right = surfaceRectInWindow.left + scaleBoundary(rect.right, bitmapWidth, surfaceWidth),
			bottom = surfaceRectInWindow.top + scaleBoundary(rect.bottom, bitmapHeight, surfaceHeight)
		).takeIf { allowZeroWidth || it.width > 0 }
	}

	val fullLeaf = mapToSurface(geometry.fullLeafRect, allowZeroWidth = false)
	val leftLeaf = mapToSurface(geometry.leftLeafRect, allowZeroWidth = false)
	val gutter = mapToSurface(geometry.gutterRect, allowZeroWidth = true)
	val rightLeaf = mapToSurface(geometry.rightLeafRect, allowZeroWidth = false)
	if (fullLeaf == null && leftLeaf == null && rightLeaf == null) return null
	return ReaderPageRasterPhysicalLayout(
		surfaceRectInWindow = ReaderPageTurnPixelRect(
			left = surfaceRectInWindow.left,
			top = surfaceRectInWindow.top,
			right = surfaceRectInWindow.right,
			bottom = surfaceRectInWindow.bottom
		),
		fullLeafRectInWindow = fullLeaf,
		leftLeafRectInWindow = leftLeaf,
		gutterRectInWindow = gutter,
		rightLeafRectInWindow = rightLeaf
	)
}

private fun readerPageRasterPhysicalLayout(snapshot: ReaderPageSlideSnapshot): ReaderPageRasterPhysicalLayout? =
	readerPageRasterPhysicalLayout(
		surfaceRectInWindow = snapshot.surfaceRectInWindow,
		bitmapWidth = snapshot.bitmap.width,
		bitmapHeight = snapshot.bitmap.height,
		geometry = snapshot.leafGeometry
	)

private fun ReaderPageTurnPixelRect?.matchesPhysicalRect(
	other: ReaderPageTurnPixelRect?,
	tolerancePixels: Int = 1
): Boolean {
	if (this == null || other == null) return this == other
	return abs(left - other.left) <= tolerancePixels &&
		abs(top - other.top) <= tolerancePixels &&
		abs(right - other.right) <= tolerancePixels &&
		abs(bottom - other.bottom) <= tolerancePixels
}

private fun ReaderPageRasterPhysicalLayout.matches(
	other: ReaderPageRasterPhysicalLayout
): Boolean = surfaceRectInWindow.matchesPhysicalRect(other.surfaceRectInWindow) &&
	fullLeafRectInWindow.matchesPhysicalRect(other.fullLeafRectInWindow) &&
	leftLeafRectInWindow.matchesPhysicalRect(other.leftLeafRectInWindow) &&
	gutterRectInWindow.matchesPhysicalRect(other.gutterRectInWindow) &&
	rightLeafRectInWindow.matchesPhysicalRect(other.rightLeafRectInWindow)

internal fun readerPageRasterPhysicalLayoutMatches(
	candidate: ReaderPageSlideSnapshot,
	reference: ReaderPageSlideSnapshot
): Boolean {
	val candidateLayout = readerPageRasterPhysicalLayout(candidate) ?: return false
	val referenceLayout = readerPageRasterPhysicalLayout(reference) ?: return false
	return candidateLayout.matches(referenceLayout)
}

private class ReaderPageBitmapRasterPixels(
	private val bitmap: Bitmap
) : ReaderPageRasterPixels {
	override val width: Int
		get() = bitmap.width
	override val height: Int
		get() = bitmap.height

	override fun readRows(top: Int, rowCount: Int, destination: IntArray) {
		bitmap.getPixels(
			destination,
			0,
			bitmap.width,
			0,
			top,
			bitmap.width,
			rowCount
		)
	}
}

private fun readerPageLiveCaptureMatchesExpected(
	candidate: ReaderPageTurnLiveCaptureResult,
	expectedTarget: ReaderPageSlideSnapshot,
	expectedSource: ReaderPageSlideSnapshot?,
	cancellationCheck: () -> Unit
): Boolean {
	val captured = candidate.captured
	fun rejected(reason: String): Boolean {
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Live page-turn capture match result=Rejected reason=$reason"
		)
		return false
	}
	if (
		captured.bitmap.isRecycled ||
		expectedTarget.bitmap.isRecycled ||
		expectedSource?.bitmap?.isRecycled == true
	) {
		return rejected("RecycledBitmap")
	}
	val kind = expectedTarget.key.kind
	val candidateGeometry = captured.geometry.leafGeometry(
		captured.bitmap.width,
		captured.bitmap.height
	) ?: return rejected("CandidateGeometryUnavailable")
	if (!readerPageRasterGeometryMatches(kind, candidateGeometry)) {
		return rejected("CandidateGeometryMismatch")
	}
	val candidateLayout = readerPageRasterPhysicalLayout(
		surfaceRectInWindow = captured.sourceRectInWindow,
		bitmapWidth = captured.bitmap.width,
		bitmapHeight = captured.bitmap.height,
		geometry = candidateGeometry
	) ?: return rejected("CandidateLayoutUnavailable")
	val targetLayout = readerPageRasterPhysicalLayout(expectedTarget)
		?: return rejected("TargetLayoutUnavailable")
	if (!candidateLayout.matches(targetLayout)) {
		return rejected("CandidateLayoutMismatch")
	}
	if (
		expectedSource != null &&
		(
			expectedSource.key.kind != kind ||
			expectedSource.key.bitmapQuality != expectedTarget.key.bitmapQuality ||
			readerPageRasterPhysicalLayout(expectedSource)?.matches(targetLayout) != true
		)
	) {
		return rejected("SourceLayoutMismatch")
	}
	if (
		!readerPageLiveRasterMatchesExpected(
			candidate = ReaderPageBitmapRasterPixels(captured.bitmap),
			expectedTarget = ReaderPageBitmapRasterPixels(expectedTarget.bitmap),
			expectedSource = expectedSource?.bitmap?.let(::ReaderPageBitmapRasterPixels),
			exactTwoTimesRoundTrip =
				readerPageLiveRasterUsesExactTwoTimesRoundTrip(candidate.diagnostics),
			cancellationCheck = cancellationCheck
		)
	) {
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Live page-turn renderer capture metrics " + candidate.diagnostics.privacySafeMetrics()
		)
		return rejected("RasterMismatch")
	}
	return true
}

private fun readerPageLiveCaptureValidationResult(
	candidate: ReaderPageTurnLiveCaptureResult,
	expectedTarget: ReaderPageSlideSnapshot,
	expectedSource: ReaderPageSlideSnapshot?,
	cancellationCheck: () -> Unit
): ReaderPageRelocationContentValidationResult =
	if (
		readerPageLiveCaptureMatchesExpected(
			candidate = candidate,
			expectedTarget = expectedTarget,
			expectedSource = expectedSource,
			cancellationCheck = cancellationCheck
		)
	) {
		ReaderPageRelocationContentValidationResult.Accepted
	} else {
		ReaderPageRelocationContentValidationResult.ContentRejected
	}

internal data class ReaderPagePreparedSnapshotGeometry(
	val surfaceRectInWindow: Rect,
	val leafGeometry: ReaderPageTurnLeafGeometry,
	val reverseFaceColor: Int
)

internal fun readerPagePreparedSnapshotGeometry(
	kind: ReaderPageTurnTransitionKind,
	captured: ReaderPageTurnCaptureResult
): ReaderPagePreparedSnapshotGeometry? {
	val bitmap = captured.bitmap
	val leafGeometry = captured.geometry.leafGeometry(bitmap.width, bitmap.height)
	if (!readerPageRasterGeometryMatches(kind, leafGeometry)) return null
	return ReaderPagePreparedSnapshotGeometry(
		surfaceRectInWindow = Rect(captured.sourceRectInWindow),
		leafGeometry = checkNotNull(leafGeometry),
		reverseFaceColor = readerPageTurnOpaqueColor(captured.geometry.reverseFaceColorArgb)
	)
}

internal fun interface ReaderPageRasterDescriptorPort {
	fun request(
		webView: WebView,
		pageIndex: Int,
		onDescriptor: (ReaderPageRasterDescriptor?) -> Unit
	)
}

internal interface ReaderPageRasterHydrationStorePort {
	suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap>?
	suspend fun remove(
		key: ReaderPageRasterKey,
		expectedMetadata: ReaderPageRasterMetadata
	): Boolean
}

internal fun interface ReaderPageRasterHydrationRequest {
	fun cancel()
}

internal enum class ReaderPageRasterHydrationDurability {
	RequiresPublication,
	PersistentStoreVerified
}

internal data class ReaderPageRasterHydrationResult(
	val snapshot: ReaderPageSlideSnapshot,
	val durability: ReaderPageRasterHydrationDurability
)

internal data class ReaderPageRasterHydrationOwnerCounts(
	val descriptorRequests: Int,
	val descriptorRecipients: Int,
	val readWorkers: Int,
	val readRecipients: Int
)

private class ReaderPageWebViewRasterDescriptorPort : ReaderPageRasterDescriptorPort {
	override fun request(
		webView: WebView,
		pageIndex: Int,
		onDescriptor: (ReaderPageRasterDescriptor?) -> Unit
	) {
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnRasterDescriptor?.($pageIndex) ?? null)"
		) { encoded -> onDescriptor(readerPageRasterDescriptor(encoded)) }
	}
}

private data class ReaderPageRasterHydrationRecipient(
	val token: Long,
	val exactRasterIdentity: String?,
	val publicationFence: () -> Boolean,
	val callback: (ReaderPageSlideSnapshot?) -> Unit,
	var descriptorPhysicalOwner: ReaderExactPhysicalOwnerRegistry.Owner? = null,
	var hydrationPhysicalOwner: ReaderExactPhysicalOwnerRegistry.Owner? = null
)

private data class ReaderPageRasterDescriptorIdentity(
	val generation: Long,
	val quality: ReaderPageBitmapQuality,
	val pageIndex: Int,
	val kind: ReaderPageTurnTransitionKind,
	val physicalLayout: ReaderPageRasterPhysicalLayout,
	val physicalLayoutEpoch: Long,
	val exactRasterIdentity: String? = null
)

private class ReaderPageRasterDescriptorRequest(
	val token: Long,
	val identity: ReaderPageRasterDescriptorIdentity,
	val webView: WeakReference<WebView>,
	val recipients: MutableMap<Long, ReaderPageRasterHydrationRecipient>,
	val physicalOwner: ReaderExactPhysicalOwnerRegistry.Owner
)

private data class ReaderPageRasterHydrationIdentity(
	val rasterIdentity: String,
	val kind: ReaderPageTurnTransitionKind,
	val physicalLayout: ReaderPageRasterPhysicalLayout,
	val physicalLayoutEpoch: Long
)

private class InFlightRasterHydration(
	val token: Long,
	val identity: ReaderPageRasterHydrationIdentity,
	val generation: Long,
	val quality: ReaderPageBitmapQuality,
	val key: ReaderPageRasterKey,
	val kind: ReaderPageTurnTransitionKind,
	val webView: WeakReference<WebView>,
	val recipients: MutableMap<Long, ReaderPageRasterHydrationRecipient>,
	val physicalOwner: ReaderExactPhysicalOwnerRegistry.Owner,
	var job: Job? = null
)

private data class ReaderFrozenSnapshotCacheEntry(
	val snapshot: ReaderPageSlideSnapshot,
	val durability: ReaderPageRasterHydrationDurability?,
	val exactRasterIdentity: String?,
	val token: ReaderLegacySourceLocalOpaqueToken
)

private data class ReaderRasterPersistenceRestartContract(
	val snapshot: WeakReference<ReaderPageSlideSnapshot>,
	val webView: WeakReference<WebView>,
	val key: ReaderPageRasterKey,
	val metadata: ReaderPageRasterMetadata,
	val captureMillis: Long,
	val priority: ReaderPageRasterPriority,
	val generation: Long,
	val physicalLayoutEpoch: Long,
	val mutationGeneration: ReaderForegroundWebViewMutationGeneration?,
	val isStillCurrent: () -> Boolean,
	val onPersisted: (ReaderPageRasterPublicationCompletion) -> Unit
)

private class ReaderRasterPersistenceRequest(
	val contract: ReaderRasterPersistenceRestartContract,
	var restartRequired: Boolean = false,
	var attemptActive: Boolean = false,
	var completed: Boolean = false
)

private class ReaderRasterGenerationPersistenceJobControl {
	private val lock = Any()
	private var job: Job? = null
	private var cancellationRequested = false
	private var ownedBitmap: Bitmap? = null
	private var transferred = false
	private var settled = false

	fun own(bitmap: Bitmap) {
		synchronized(lock) {
			check(!settled && ownedBitmap == null)
			ownedBitmap = bitmap
		}
	}

	fun transfer() {
		synchronized(lock) {
			transferred = true
			ownedBitmap = null
		}
	}

	fun settle(cleanup: (Bitmap?, Boolean) -> Unit): Boolean {
		val bitmap = synchronized(lock) {
			if (settled) return false
			settled = true
			(ownedBitmap to transferred).also { ownedBitmap = null }
		}
		cleanup(bitmap.first, bitmap.second)
		return true
	}

	fun attach(job: Job) {
		val cancelNow = synchronized(lock) {
			check(this.job == null) { "Raster persistence owner already has a job" }
			this.job = job
			cancellationRequested
		}
		if (cancelNow) job.cancel()
	}

	fun cancel(): Boolean {
		val jobToCancel = synchronized(lock) {
			cancellationRequested = true
			job
		}
		jobToCancel?.cancel()
		return true
	}
}

private enum class ReaderPageTurnBundleRestorationStep {
	Bitmap,
	LiveValidation,
	HydrationScheduler,
	HydrationOwners,
	PublicationScheduler,
	PublicationLedger,
	RasterGenerationAndPersistence,
	PendingDescriptors,
	DescriptorRequests,
	PersistentStore,
	RasterCache,
	SnapshotCache,
	Teardown
}

internal data class ReaderPageRasterPublicationValue<T : Any>(
	val key: ReaderPageRasterKey,
	val generation: ReaderPageRasterGeneration<T>
)

internal fun readerPageRasterPublicationCompletion(
	persistedForCurrentPublication: Boolean,
	publicationCurrent: Boolean,
	writeFailureReason: ReaderPageRasterWriteFailureReason?
): ReaderPageRasterPublicationCompletion = when {
	persistedForCurrentPublication -> ReaderPageRasterPublicationCompletion(
		ReaderPageRasterPublicationResult.Durable
	)
	publicationCurrent &&
		writeFailureReason == ReaderPageRasterWriteFailureReason.DiskCapacity ->
		ReaderPageRasterPublicationCompletion(
			result = ReaderPageRasterPublicationResult.CapacityReached,
			writeFailureReason = ReaderPageRasterWriteFailureReason.DiskCapacity
		)
	publicationCurrent -> ReaderPageRasterPublicationCompletion(
		result = ReaderPageRasterPublicationResult.Failed,
		writeFailureReason = writeFailureReason
	)
	else -> ReaderPageRasterPublicationCompletion(
		ReaderPageRasterPublicationResult.Failed
	)
}

internal data class ReaderPageTurnBundleOwnershipMetrics(
	val rasterCache: ReaderPageRasterCacheMetrics,
	val stagedPublications: Int,
	val stagedPublicationLimit: Int,
	val pendingPublicationCallbacks: Int,
	val pendingPublicationCallbackLimit: Int
)

private class ReaderPageLiveValidationCaptureStage(
	private val ownership: ReaderExactPhysicalOwnerRegistry,
	private val cancelPhysical: () -> Boolean,
	private val onOwnershipMutated: () -> Unit
) {
	private val lock = Any()
	private var owners: List<ReaderExactPhysicalOwnerRegistry.Owner>? = null
	private var cancellationIssued = false

	fun attach(admitted: List<ReaderExactPhysicalOwnerRegistry.Owner>) {
		synchronized(lock) {
			check(owners == null) { "Live validation capture ownership attached twice" }
			owners = admitted
		}
	}

	fun cancel(): Boolean {
		val shouldCancel = synchronized(lock) {
			if (cancellationIssued) return true
			cancellationIssued = true
			true
		}
		if (!shouldCancel) return true
		val accepted = runCatching(cancelPhysical).getOrDefault(false)
		if (!accepted) synchronized(lock) { cancellationIssued = false }
		return accepted
	}

	fun complete() {
		val completed = synchronized(lock) { owners.also { owners = null } }
		completed?.let {
			ownership.complete(it)
			onOwnershipMutated()
		}
	}
}

private class ReaderPageLiveValidationCallbackStage(
	private val ownership: ReaderExactPhysicalOwnerRegistry,
	private val cancelExternal: () -> Unit,
	private val onCancelled: () -> Unit,
	private val onOwnershipMutated: () -> Unit
) {
	private enum class State { Open, Running, Cancelled, Terminal }

	private val lock = Any()
	private var owner: ReaderExactPhysicalOwnerRegistry.Owner? = null
	private var state = State.Open

	fun attach(admitted: ReaderExactPhysicalOwnerRegistry.Owner) {
		synchronized(lock) {
			check(owner == null) { "Live validation callback ownership attached twice" }
			owner = admitted
		}
	}

	fun run(action: () -> Unit): Boolean {
		val admitted = synchronized(lock) {
			if (state != State.Open) return false
			state = State.Running
			owner
		}
		try {
			action()
		} finally {
			synchronized(lock) { state = State.Terminal }
			admitted?.let {
				ownership.complete(it)
				onOwnershipMutated()
			}
		}
		return true
	}

	fun cancel(): Boolean {
		var completeNow = false
		val accepted = synchronized(lock) {
			when (state) {
				State.Open -> {
					state = State.Terminal
					completeNow = true
					true
				}
				State.Running -> {
					state = State.Cancelled
					true
				}
				State.Cancelled,
				State.Terminal -> true
			}
		}
		if (!accepted) return false
		runCatching(cancelExternal)
		onCancelled()
		if (completeNow) {
			synchronized(lock) { owner }?.let {
				ownership.complete(it)
				onOwnershipMutated()
			}
		}
		return true
	}
}

internal class ReaderPageTurnBundleSource(
	private val bitmapSource: ReaderPageTurnBitmapSource = ReaderPageTurnBitmapSource(),
	private val mainHandler: Handler = Handler(Looper.getMainLooper()),
	private val descriptorPort: ReaderPageRasterDescriptorPort =
		ReaderPageWebViewRasterDescriptorPort(),
	private val hydrationStorePort: ReaderPageRasterHydrationStorePort? = null,
	private val diagnostics: ReaderPageRuntimeDiagnostics? = null,
	private val qaFaultRegistry: ReaderPageQaFaultRegistry? = null,
	private val onOwnershipMutated: () -> Unit = {},
	private val hydrationOwnershipTokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val publicationOwnershipTokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val descriptorOwnershipTokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val snapshotCacheOwnershipTokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val liveValidationOwnershipTokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val storeAndCacheOwnershipTokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val hydrationSchedulerOverride: ReaderPageRasterHydrationScheduler? = null,
	private val publicationSchedulerOverride: ReaderPageRasterPublicationScheduler? = null,
	private val pendingDescriptorOwnersOverride:
		ReaderPagePendingCallbackOwners<ReaderPageSlideSnapshot>? = null,
	private val liveValidationDispatcher: CoroutineDispatcher = Dispatchers.Default,
	private val rasterInitializationDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
	private var activeGeneration = 0L
	private var bitmapQuality = ReaderPageBitmapQuality.Balanced
	private val rasterJob = SupervisorJob()
	private val rasterScope = CoroutineScope(rasterJob + Dispatchers.Main.immediate)
	private val teardownJob = SupervisorJob()
	private val teardownScope = CoroutineScope(teardownJob + Dispatchers.Default)
	private val closeFenceLock = Any()
	private val closeFenceCompletion = CompletableDeferred<Unit>()
	private val liveValidationAdmissionLock = Any()
	private val activeLiveValidations =
		linkedSetOf<ReaderPageRelocationContentValidationHandle>()
	private val liveValidationOwnership = ReaderExactPhysicalOwnerRegistry(
		ReaderLegacyInventorySource.RasterLiveValidation,
		liveValidationOwnershipTokenAllocator
	)
	private val teardownOwnership = ReaderExactPhysicalOwnerRegistry(
		ReaderLegacyInventorySource.RasterStoreAndCache,
		storeAndCacheOwnershipTokenAllocator
	)
	private val teardownOwnershipLock = Any()
	private var teardownPhysicalOwner: ReaderExactPhysicalOwnerRegistry.Owner? = null
	private var teardownOwnershipRegistered = false
	private val rasterGenerationAndPersistenceOwnershipTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator()
	private val rasterGenerationAndPersistenceOwnership = ReaderExactPhysicalOwnerRegistry(
		ReaderLegacyInventorySource.RasterGenerationAndPersistence,
		rasterGenerationAndPersistenceOwnershipTokenAllocator
	)
	private val rasterInitializationFenceLock = Any()
	private val rasterInitializationMutex = Mutex()
	private val rasterPersistenceJobLock = Any()
	private val rasterPersistenceJobs = linkedSetOf<Job>()
	private val rasterPersistenceRequests = linkedSetOf<ReaderRasterPersistenceRequest>()
	private val rasterPersistenceAttempts = linkedSetOf<ReaderRasterGenerationPersistenceJobControl>()
	private val rasterInitializationRestarts = linkedSetOf<WeakReference<WebView>>()
	private val descriptorOwnershipAdmissionLock = Any()
	private val descriptorRequestOwnership = ReaderExactPhysicalOwnerRegistry(
		ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback,
		descriptorOwnershipTokenAllocator
	)
	private val descriptorPhysicalRecipients = linkedMapOf<
		Long,
		ReaderPageRasterHydrationRecipient
	>()
	private val pendingDescriptorOwners = pendingDescriptorOwnersOverride
		?: ReaderPagePendingCallbackOwners<ReaderPageSlideSnapshot>(
			retain = ReaderPageSlideSnapshot::retain,
			release = ReaderPageSlideSnapshot::release,
			tokenAllocator = descriptorOwnershipTokenAllocator
		)
	private val visualStateRequestId = AtomicLong()
	private val persistenceAttemptIds = AtomicLong()
	private val rasterPhysicalLayoutEpoch = AtomicLong()
	private var physicalLayoutAuthority: ReaderPageRasterPhysicalLayoutAuthority? = null
	private val snapshotCache = LinkedHashMap<ReaderPageSlideSnapshotKey, ReaderPageSlideSnapshot>(0, 0.75f, true)
	private val snapshotCacheTokens =
		IdentityHashMap<ReaderPageSlideSnapshot, ReaderLegacySourceLocalOpaqueToken>()
	private val frozenSnapshotCacheEntries =
		linkedMapOf<ReaderLegacySourceLocalOpaqueToken, ReaderFrozenSnapshotCacheEntry>()
	@Volatile
	private var frozenSnapshotCacheDomain: ReaderLegacyPhysicalDomain? = null
	private var restorationProgressDomain: ReaderLegacyPhysicalDomain? = null
	private val restoredTransitionSteps =
		linkedSetOf<ReaderPageTurnBundleRestorationStep>()
	private val snapshotDurability =
		IdentityHashMap<ReaderPageSlideSnapshot, ReaderPageRasterHydrationDurability>()
	private val snapshotExactRasterIdentities =
		IdentityHashMap<ReaderPageSlideSnapshot, String>()
	private val descriptorRequests =
		mutableMapOf<Long, ReaderPageRasterDescriptorRequest>()
	private val descriptorRequestTokens =
		mutableMapOf<ReaderPageRasterDescriptorIdentity, Long>()
	private val rasterDescriptors =
		linkedMapOf<ReaderPageRasterDescriptorIdentity, ReaderPageRasterDescriptor>()
	private val hydrationOwnershipAdmissionLock = Any()
	private val hydrationOwnership = ReaderExactPhysicalOwnerRegistry(
		ReaderLegacyInventorySource.RasterHydration,
		hydrationOwnershipTokenAllocator
	)
	private val inFlightRasterHydrations =
		mutableMapOf<ReaderPageRasterHydrationIdentity, InFlightRasterHydration>()
	private val hydrationScheduler = hydrationSchedulerOverride ?: ReaderPageRasterHydrationScheduler(
		scope = rasterScope,
		maxConcurrentWorkers = 2,
		tokenAllocator = hydrationOwnershipTokenAllocator
	)
	private var nextHydrationToken = 0L
	private val publicationScheduler = publicationSchedulerOverride ?: ReaderPageRasterPublicationScheduler(
		scope = rasterScope,
		maxConcurrentWorkers = 1,
		tokenAllocator = publicationOwnershipTokenAllocator
	)
	private val publicationLedger =
		ReaderPageRasterPublicationLedger<
			ReaderPageRasterPublicationValue<Bitmap>
		>(
			currentEpochEntryLimit =
				ReaderPageMaximumForegroundPublicationEntries +
					ReaderPageAdjacentPrefetchPublicationAllowance,
			persistenceWorkerLimit = publicationScheduler.maxConcurrentWorkers,
			callbackLimit = ReaderPageMaximumPublicationCallbacks,
			onOwnershipMutated = onOwnershipMutated,
			tokenAllocator = publicationOwnershipTokenAllocator,
			release = { value ->
				ReaderAndroidPageRasterCodec.release(value.generation.value)
			}
		)
	private val publicationCompletionResults =
		ConcurrentHashMap<
			ReaderPageRasterPublicationRequest,
			ReaderPageRasterPublicationCompletion
		>()
	private val rasterPersistenceDiagnostics = linkedSetOf<String>()
	private val persistenceRetryCorrelations =
		mutableMapOf<String, ReaderPageQaFaultCorrelation>()
	private var protectedSnapshotPageIndices = emptySet<Int>()
	private var protectedEncodedCenterPageIndex: Int? = null
	private var protectedEncodedPageIndices = emptySet<Int>()
	private var protectedEncodedProfile: ReaderPageRasterProfile? = null
	private var rasterCache: ReaderPageRasterCache<Bitmap>? = null
	private var persistentStore: ReaderPageRasterCacheStore<Bitmap>? = null
	private val rasterReferenceLifecycleLock = Any()
	private var rasterPhysicalCloseFinished = false
	private var rasterScheduler: ReaderPageRasterScheduler<Bitmap>? = null
	private var activeWebView = WeakReference<WebView>(null)
	@Volatile
	private var closed = false
	private var closeInvalidationFailure: Throwable? = null
	private var disposedRasterCacheMetrics: ReaderPageRasterCacheMetrics? = null
	private val teardown = ReaderPageTurnBundleTeardown(
		scope = teardownScope,
		preCloseFailure = { closeInvalidationFailure },
		closePublicationWorkers = {
			publicationScheduler.closeAndJoin()
			check(publicationCompletionResults.isEmpty()) {
				"Raster publication completion results did not drain"
			}
		},
		publicationEntryCount = publicationLedger::entryCount,
		publicationDispatchFailure = publicationLedger::dispatchFailure,
		closeRasterGenerationWorkers = {
			rasterScheduler?.closeAndJoin()
			val persistenceJobs = synchronized(rasterPersistenceJobLock) {
				rasterPersistenceJobs.toList()
			}
			persistenceJobs.forEach { job -> job.join() }
			rasterScheduler?.closeAndJoin()
			check(synchronized(rasterPersistenceJobLock) {
				rasterPersistenceJobs.isEmpty()
			}) { "Raster persistence initialization workers did not drain" }
			pendingDescriptorOwners.awaitCloseCompletion()
			check(pendingDescriptorOwners.pendingCount() == 0) {
				"Raster descriptor callbacks retained snapshot owners"
			}
		},
		closeRasterHydrationWorkers = {
			hydrationScheduler.closeAndJoin()
			rasterJob.join()
			check(synchronized(closeFenceLock) { activeLiveValidations.isEmpty() }) {
				"Live raster validation workers did not drain"
			}
		},
		closePersistentStore = {
			persistentStore?.close()
		},
		closeRasterCache = {
			rasterCache?.let { cache ->
				try {
					cache.close()
				} finally {
					disposedRasterCacheMetrics = cache.metrics()
				}
			}
		},
		clearReferences = {
			activeWebView.clear()
			synchronized(rasterReferenceLifecycleLock) {
				rasterPhysicalCloseFinished = true
			}
			clearClosedRasterReferencesIfSettled()
		},
		onFinished = {
			val physicalOwner = synchronized(teardownOwnershipLock) {
				teardownPhysicalOwner.also { teardownPhysicalOwner = null }
			}
			physicalOwner?.let(teardownOwnership::complete)
			clearClosedRasterReferencesIfSettled()
			teardownJob.complete()
		}
	)
	private val closeCompletion = teardownScope.async(start = CoroutineStart.LAZY) {
		closeFenceCompletion.await()
		teardown.start().await()
	}
	val isAvailable: Boolean
		get() = bitmapSource.isAvailable

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		frozenSnapshotCacheDomain?.let { frozenDomain ->
			return if (frozenDomain == domain) {
				ReaderPortCommandResult.Accepted
			} else {
				ReaderPortCommandResult.Rejected(
					ReaderTransitionFailureReason.InvalidLegacyResource
				)
			}
		}
		val bitmapResult = bitmapSource.freezeForTransitionActivation(domain)
		if (bitmapResult != ReaderPortCommandResult.Accepted) return bitmapResult
		val generationAndPersistenceResult = synchronized(rasterInitializationFenceLock) {
			rasterGenerationAndPersistenceOwnership.freezeForTransitionActivation(domain)
		}
		if (generationAndPersistenceResult != ReaderPortCommandResult.Accepted) {
			bitmapSource.restoreAfterTransitionActivation(domain)
			return generationAndPersistenceResult
		}
		val schedulerResult = synchronized(rasterInitializationFenceLock) {
			rasterScheduler?.freezeForTransitionActivation(domain)
				?: ReaderPortCommandResult.Accepted
		}
		if (schedulerResult != ReaderPortCommandResult.Accepted) {
			rasterGenerationAndPersistenceOwnership.restoreAfterTransitionActivation(domain)
			bitmapSource.restoreAfterTransitionActivation(domain)
			return schedulerResult
		}
		val validationResult = synchronized(liveValidationAdmissionLock) {
			liveValidationOwnership.freezeForTransitionActivation(domain)
		}
		if (validationResult != ReaderPortCommandResult.Accepted) {
			restoreRasterGenerationAndPersistenceOwnership(domain)
			bitmapSource.restoreAfterTransitionActivation(domain)
			return validationResult
		}
		val hydrationResult = synchronized(hydrationOwnershipAdmissionLock) {
			val physicalResult = hydrationOwnership.freezeForTransitionActivation(domain)
			if (physicalResult != ReaderPortCommandResult.Accepted) {
				physicalResult
			} else {
				val schedulerResult = hydrationScheduler.freezeForTransitionActivation(domain)
				if (schedulerResult != ReaderPortCommandResult.Accepted) {
					hydrationOwnership.restoreAfterTransitionActivation(domain)
				}
				schedulerResult
			}
		}
		if (hydrationResult != ReaderPortCommandResult.Accepted) {
			liveValidationOwnership.restoreAfterTransitionActivation(domain)
			restoreRasterGenerationAndPersistenceOwnership(domain)
			bitmapSource.restoreAfterTransitionActivation(domain)
			return hydrationResult
		}
		val publicationResult = publicationScheduler.freezeForTransitionActivation(domain)
		if (publicationResult != ReaderPortCommandResult.Accepted) {
			restoreHydrationOwnership(domain)
			liveValidationOwnership.restoreAfterTransitionActivation(domain)
			restoreRasterGenerationAndPersistenceOwnership(domain)
			bitmapSource.restoreAfterTransitionActivation(domain)
			return publicationResult
		}
		val ledgerResult = publicationLedger.freezeForTransitionActivation(domain)
		if (ledgerResult != ReaderPortCommandResult.Accepted) {
			publicationScheduler.restoreAfterTransitionActivation(domain)
			restoreHydrationOwnership(domain)
			liveValidationOwnership.restoreAfterTransitionActivation(domain)
			restoreRasterGenerationAndPersistenceOwnership(domain)
			bitmapSource.restoreAfterTransitionActivation(domain)
			return ledgerResult
		}
		val descriptorResult = synchronized(descriptorOwnershipAdmissionLock) {
			val requestResult = descriptorRequestOwnership.freezeForTransitionActivation(domain)
			if (requestResult != ReaderPortCommandResult.Accepted) {
				requestResult
			} else {
				val pendingResult = pendingDescriptorOwners.freezeForTransitionActivation(domain)
				if (pendingResult != ReaderPortCommandResult.Accepted) {
					descriptorRequestOwnership.restoreAfterTransitionActivation(domain)
				}
				pendingResult
			}
		}
		if (descriptorResult != ReaderPortCommandResult.Accepted) {
			publicationLedger.restoreAfterTransitionActivation(domain)
			publicationScheduler.restoreAfterTransitionActivation(domain)
			restoreHydrationOwnership(domain)
			liveValidationOwnership.restoreAfterTransitionActivation(domain)
			restoreRasterGenerationAndPersistenceOwnership(domain)
			bitmapSource.restoreAfterTransitionActivation(domain)
			return descriptorResult
		}
		val storeResult = persistentStore?.freezeForTransitionActivation(domain)
			?: ReaderPortCommandResult.Accepted
		if (storeResult != ReaderPortCommandResult.Accepted) {
			restoreDescriptorOwnership(domain)
			publicationLedger.restoreAfterTransitionActivation(domain)
			publicationScheduler.restoreAfterTransitionActivation(domain)
			restoreHydrationOwnership(domain)
			liveValidationOwnership.restoreAfterTransitionActivation(domain)
			restoreRasterGenerationAndPersistenceOwnership(domain)
			bitmapSource.restoreAfterTransitionActivation(domain)
			return storeResult
		}
		val cacheResult = rasterCache?.freezeForTransitionActivation(domain)
			?: ReaderPortCommandResult.Accepted
		if (cacheResult != ReaderPortCommandResult.Accepted) {
			persistentStore?.restoreAfterTransitionActivation(domain)
			restoreDescriptorOwnership(domain)
			publicationLedger.restoreAfterTransitionActivation(domain)
			publicationScheduler.restoreAfterTransitionActivation(domain)
			restoreHydrationOwnership(domain)
			liveValidationOwnership.restoreAfterTransitionActivation(domain)
			restoreRasterGenerationAndPersistenceOwnership(domain)
			bitmapSource.restoreAfterTransitionActivation(domain)
			return cacheResult
		}
		val teardownResult = synchronized(teardownOwnershipLock) {
			teardownOwnership.freezeForTransitionActivation(domain).also { result ->
				if (result == ReaderPortCommandResult.Accepted) {
					frozenSnapshotCacheDomain = domain
				}
			}
		}
		if (teardownResult != ReaderPortCommandResult.Accepted) {
			rasterCache?.restoreAfterTransitionActivation(domain)
			persistentStore?.restoreAfterTransitionActivation(domain)
			restoreDescriptorOwnership(domain)
			publicationLedger.restoreAfterTransitionActivation(domain)
			publicationScheduler.restoreAfterTransitionActivation(domain)
			restoreHydrationOwnership(domain)
			liveValidationOwnership.restoreAfterTransitionActivation(domain)
			restoreRasterGenerationAndPersistenceOwnership(domain)
			bitmapSource.restoreAfterTransitionActivation(domain)
			return teardownResult
		}
		return ReaderPortCommandResult.Accepted
	}

	private fun restoreHydrationOwnership(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(hydrationOwnershipAdmissionLock) {
		val schedulerResult = hydrationScheduler.restoreAfterTransitionActivation(domain)
		val physicalResult = hydrationOwnership.restoreAfterTransitionActivation(domain)
		if (
			schedulerResult == ReaderPortCommandResult.Accepted &&
			physicalResult == ReaderPortCommandResult.Accepted
		) {
			ReaderPortCommandResult.Accepted
		} else {
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
	}

	private fun restoreRasterGenerationAndPersistenceOwnership(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		if (rasterGenerationAndPersistenceOwnership.snapshotFrozenOwnership().any {
				it.state != ReaderLegacyResourceState.Released
			}) return invalidRestorationResource()
		val physicalResult = if (rasterGenerationAndPersistenceOwnership.isFrozen) {
			rasterGenerationAndPersistenceOwnership.restoreAfterTransitionActivation(domain)
		} else {
			ReaderPortCommandResult.Accepted
		}
		if (physicalResult != ReaderPortCommandResult.Accepted) return physicalResult
		val schedulerResult = rasterScheduler?.let { scheduler ->
			if (scheduler.isFrozen) scheduler.restoreAfterTransitionActivation(domain)
			else ReaderPortCommandResult.Accepted
		} ?: ReaderPortCommandResult.Accepted
		return schedulerResult
	}

	private fun resumeRasterGenerationAndPersistenceRequests() {
		val restarts = synchronized(rasterInitializationFenceLock) {
			if (closed || frozenSnapshotCacheDomain != null) return
			val initialization = rasterInitializationRestarts.toList()
			rasterInitializationRestarts.clear()
			initialization to rasterPersistenceRequests.filter { it.restartRequired && !it.attemptActive && !it.completed }
		}
		restarts.first.forEach { reference ->
			reference.get()?.takeIf { it.isAttachedToWindow }?.let { webView ->
				rasterScope.launch { initializeRasterCache(webView) }
			}
		}
		restarts.second.forEach(::launchRasterPersistenceRequest)
	}

	private fun restoreDescriptorOwnership(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(descriptorOwnershipAdmissionLock) {
		val pendingResult = pendingDescriptorOwners.restoreAfterTransitionActivation(domain)
		val requestResult = descriptorRequestOwnership.restoreAfterTransitionActivation(domain)
		if (
			pendingResult == ReaderPortCommandResult.Accepted &&
			requestResult == ReaderPortCommandResult.Accepted
		) {
			ReaderPortCommandResult.Accepted
		} else {
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> =
		bitmapSource.snapshotFrozenOwnership() +
			liveValidationOwnership.snapshotFrozenOwnership() +
			hydrationOwnership.snapshotFrozenOwnership() +
			hydrationScheduler.snapshotFrozenOwnership() +
			publicationScheduler.snapshotFrozenOwnership() +
			publicationLedger.snapshotFrozenOwnership() +
			rasterGenerationAndPersistenceOwnership.snapshotFrozenOwnership() +
			rasterScheduler.orEmptyFrozenOwnership() +
			descriptorRequestOwnership.snapshotFrozenOwnership() +
			pendingDescriptorOwners.snapshotFrozenOwnership() +
			snapshotFrozenCacheOwnership() +
			persistentStore.orEmptyFrozenOwnership() +
			rasterCache.orEmptyFrozenOwnership() +
			teardownOwnership.snapshotFrozenOwnership()

	private fun ReaderPageRasterScheduler<Bitmap>?.orEmptyFrozenOwnership():
		List<ReaderFrozenLegacyResource> = this?.snapshotFrozenOwnership().orEmpty()

	private fun ReaderPageRasterCacheStore<Bitmap>?.orEmptyFrozenOwnership():
		List<ReaderFrozenLegacyResource> = this?.snapshotFrozenOwnership().orEmpty()

	private fun ReaderPageRasterCache<Bitmap>?.orEmptyFrozenOwnership():
		List<ReaderFrozenLegacyResource> = this?.snapshotFrozenOwnership().orEmpty()

	private fun clearClosedRasterReferencesIfSettled() {
		synchronized(rasterReferenceLifecycleLock) {
			if (!rasterPhysicalCloseFinished) return
			val storeSettled = persistentStore.orEmptyFrozenOwnership().isEmpty()
			val cacheSettled = rasterCache.orEmptyFrozenOwnership().isEmpty()
			val teardownSettled = teardownOwnership.snapshotFrozenOwnership().isEmpty()
			if (storeSettled && cacheSettled && teardownSettled) {
				persistentStore = null
				rasterCache = null
			}
		}
	}

	fun snapshotConnectedFrozenOwnership(): List<ReaderLegacyConnectedSourceInventory>? {
		val domain = frozenSnapshotCacheDomain ?: return null
		val resourcesBySource = snapshotFrozenOwnership().groupBy {
			it.physicalIdentity.source
		}
		return ReaderPageTurnBundleInventorySources.map { source ->
			ReaderLegacyConnectedSourceInventory(
				source = source,
				domain = domain,
				resources = resourcesBySource[source].orEmpty()
			)
		}
	}

	private fun snapshotFrozenCacheOwnership(): List<ReaderFrozenLegacyResource> {
		val domain = frozenSnapshotCacheDomain ?: return emptyList()
		return snapshotCache.values.map { snapshot ->
			ReaderFrozenLegacyResource(
				freezeToken = domain.freezeToken,
				physicalIdentity = ReaderLegacyPhysicalIdentity(
					domain = domain,
					source = ReaderLegacyInventorySource.RasterSnapshotCache,
					sourceLocalToken = checkNotNull(snapshotCacheTokens[snapshot])
				),
				kind = ReaderTransitionResourceKind.Raster,
				binding = null,
				visibleOwner = null,
				origin = ReaderLegacyResourceOrigin.Owned,
				state = ReaderLegacyResourceState.Prepared,
				mayBeCommittedPredecessor = false
			)
		}
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult = when (physicalIdentity.source) {
		ReaderLegacyInventorySource.RasterGenerationAndPersistence -> {
			val physicalResult = rasterGenerationAndPersistenceOwnership
				.drainFrozenOwnership(physicalIdentity, onConfirmed)
			if (physicalResult == ReaderPortCommandResult.Accepted) {
				physicalResult
			} else {
				rasterScheduler?.drainFrozenOwnership(physicalIdentity, onConfirmed)
					?: ReaderPortCommandResult.Rejected(
						ReaderTransitionFailureReason.InvalidLegacyResource
					)
			}
		}
		ReaderLegacyInventorySource.RasterCaptureAndVisualState ->
			bitmapSource.drainFrozenOwnership(physicalIdentity, onConfirmed)
		ReaderLegacyInventorySource.RasterLiveValidation ->
			liveValidationOwnership.drainFrozenOwnership(physicalIdentity, onConfirmed)
		ReaderLegacyInventorySource.RasterStoreAndCache -> {
			val storeResult = persistentStore?.drainFrozenOwnership(
				physicalIdentity,
				onConfirmed
			)
			val result = if (storeResult == ReaderPortCommandResult.Accepted) {
				storeResult
			} else {
				val cacheResult = rasterCache?.drainFrozenOwnership(
					physicalIdentity,
					onConfirmed
				)
				if (cacheResult == ReaderPortCommandResult.Accepted) {
					cacheResult
				} else {
					teardownOwnership.drainFrozenOwnership(physicalIdentity, onConfirmed)
				}
			}
			if (result == ReaderPortCommandResult.Accepted) {
				clearClosedRasterReferencesIfSettled()
			}
			result
		}
		ReaderLegacyInventorySource.RasterSnapshotCache ->
			drainFrozenSnapshotCacheOwnership(physicalIdentity, onConfirmed)
		ReaderLegacyInventorySource.RasterHydration -> {
			val schedulerResult = hydrationScheduler.drainFrozenOwnership(
				physicalIdentity,
				onConfirmed
			)
			if (schedulerResult == ReaderPortCommandResult.Accepted) {
				schedulerResult
			} else {
				hydrationOwnership.drainFrozenOwnership(physicalIdentity, onConfirmed)
			}
		}
		ReaderLegacyInventorySource.RasterDescriptorAndPendingCallback -> {
			val pendingResult = pendingDescriptorOwners.drainFrozenOwnership(
				physicalIdentity,
				onConfirmed
			)
			if (pendingResult == ReaderPortCommandResult.Accepted) {
				pendingResult
			} else {
				descriptorRequestOwnership.drainFrozenOwnership(
					physicalIdentity,
					onConfirmed
				)
			}
		}
		ReaderLegacyInventorySource.RasterPublication -> {
			val schedulerResult = publicationScheduler.drainFrozenOwnership(
				physicalIdentity,
				onConfirmed
			)
			if (schedulerResult == ReaderPortCommandResult.Accepted) schedulerResult
			else publicationLedger.drainFrozenOwnership(physicalIdentity, onConfirmed)
		}
		else -> ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
	}

	private fun drainFrozenSnapshotCacheOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		val domain = frozenSnapshotCacheDomain
		if (
			domain == null ||
			physicalIdentity.domain != domain ||
			physicalIdentity.source != ReaderLegacyInventorySource.RasterSnapshotCache
		) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		val snapshot = snapshotCache.values.firstOrNull {
			snapshotCacheTokens[it] == physicalIdentity.sourceLocalToken
		} ?: return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		snapshot.retain()
		val restart = ReaderFrozenSnapshotCacheEntry(
			snapshot = snapshot,
			durability = snapshotDurability[snapshot],
			exactRasterIdentity = snapshotExactRasterIdentities[snapshot],
			token = physicalIdentity.sourceLocalToken
		)
		val removed = removeCachedSnapshot(
			key = snapshot.key,
			expected = snapshot,
			retainTokenForRestart = true
		) ?: run {
			snapshot.release()
			return ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
		frozenSnapshotCacheEntries[restart.token] = restart
		removed.releaseCacheOwnership()
		onConfirmed(physicalIdentity)
		return ReaderPortCommandResult.Accepted
	}

	private inline fun restoreTransitionStep(
		step: ReaderPageTurnBundleRestorationStep,
		restore: () -> ReaderPortCommandResult
	): ReaderPortCommandResult {
		if (step in restoredTransitionSteps) return ReaderPortCommandResult.Accepted
		return restore().also { result ->
			if (result == ReaderPortCommandResult.Accepted) {
				restoredTransitionSteps += step
			}
		}
	}

	private fun invalidRestorationResource(): ReaderPortCommandResult =
		ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)

	private fun restoreFrozenSnapshotCacheEntries() {
		frozenSnapshotCacheEntries.values.forEach { restart ->
			val snapshot = restart.snapshot
			snapshot.transferRetainToCacheOwnership()
			snapshotCache[snapshot.key] = snapshot
			snapshotCacheTokens[snapshot] = restart.token
			restart.durability?.let { snapshotDurability[snapshot] = it }
			restart.exactRasterIdentity?.let {
				snapshotExactRasterIdentities[snapshot] = it
			}
		}
		frozenSnapshotCacheEntries.clear()
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		if (frozenSnapshotCacheDomain != domain || closed) return invalidRestorationResource()
		val progressDomain = restorationProgressDomain
		if (progressDomain != null && progressDomain != domain) {
			return invalidRestorationResource()
		}
		val activeTokens = snapshotCache.values.map { snapshot ->
			snapshotCacheTokens[snapshot] ?: return invalidRestorationResource()
		}
		val restartEntriesAreValid = frozenSnapshotCacheEntries.all { (token, restart) ->
			restart.token == token &&
				snapshotCache[restart.snapshot.key] == null &&
				snapshotCacheTokens[restart.snapshot] == token
		}
		if (
			activeTokens.toSet().size != activeTokens.size ||
			activeTokens.any(frozenSnapshotCacheEntries::containsKey) ||
			!restartEntriesAreValid
		) return invalidRestorationResource()
		restorationProgressDomain = domain

		restoreTransitionStep(ReaderPageTurnBundleRestorationStep.Bitmap) {
			bitmapSource.restoreAfterTransitionActivation(domain)
		}
		restoreTransitionStep(ReaderPageTurnBundleRestorationStep.LiveValidation) {
			liveValidationOwnership.restoreAfterTransitionActivation(domain)
		}
		restoreTransitionStep(ReaderPageTurnBundleRestorationStep.HydrationScheduler) {
			synchronized(hydrationOwnershipAdmissionLock) {
				hydrationScheduler.restoreAfterTransitionActivation(domain)
			}
		}
		restoreTransitionStep(ReaderPageTurnBundleRestorationStep.HydrationOwners) {
			synchronized(hydrationOwnershipAdmissionLock) {
				hydrationOwnership.restoreAfterTransitionActivation(domain)
			}
		}
		restoreTransitionStep(ReaderPageTurnBundleRestorationStep.PublicationScheduler) {
			publicationScheduler.restoreAfterTransitionActivation(domain)
		}
		restoreTransitionStep(ReaderPageTurnBundleRestorationStep.PublicationLedger) {
			publicationLedger.restoreAfterTransitionActivation(domain)
		}
		restoreTransitionStep(ReaderPageTurnBundleRestorationStep.PendingDescriptors) {
			synchronized(descriptorOwnershipAdmissionLock) {
				pendingDescriptorOwners.restoreAfterTransitionActivation(domain)
			}
		}
		restoreTransitionStep(ReaderPageTurnBundleRestorationStep.DescriptorRequests) {
			synchronized(descriptorOwnershipAdmissionLock) {
				descriptorRequestOwnership.restoreAfterTransitionActivation(domain)
			}
		}
		restoreTransitionStep(ReaderPageTurnBundleRestorationStep.PersistentStore) {
			persistentStore?.restoreAfterTransitionActivation(domain)
				?: ReaderPortCommandResult.Accepted
		}
		restoreTransitionStep(ReaderPageTurnBundleRestorationStep.RasterCache) {
			rasterCache?.restoreAfterTransitionActivation(domain)
				?: ReaderPortCommandResult.Accepted
		}
		val preSnapshotSteps = ReaderPageTurnBundleRestorationStep.entries -
			setOf(
				ReaderPageTurnBundleRestorationStep.RasterGenerationAndPersistence,
				ReaderPageTurnBundleRestorationStep.SnapshotCache,
				ReaderPageTurnBundleRestorationStep.Teardown
			)
		if (!restoredTransitionSteps.containsAll(preSnapshotSteps)) {
			return invalidRestorationResource()
		}
		restoreTransitionStep(ReaderPageTurnBundleRestorationStep.SnapshotCache) {
			restoreFrozenSnapshotCacheEntries()
			ReaderPortCommandResult.Accepted
		}
		val preTeardownSteps = ReaderPageTurnBundleRestorationStep.entries -
			setOf(ReaderPageTurnBundleRestorationStep.Teardown, ReaderPageTurnBundleRestorationStep.RasterGenerationAndPersistence)
		if (!restoredTransitionSteps.containsAll(preTeardownSteps)) {
			return invalidRestorationResource()
		}

		val teardownResult = synchronized(teardownOwnershipLock) {
			restoreTransitionStep(ReaderPageTurnBundleRestorationStep.Teardown) {
				teardownOwnership.restoreAfterTransitionActivation(domain)
			}
		}
		if (teardownResult != ReaderPortCommandResult.Accepted) return invalidRestorationResource()
		val generationResult = restoreTransitionStep(ReaderPageTurnBundleRestorationStep.RasterGenerationAndPersistence) {
			restoreRasterGenerationAndPersistenceOwnership(domain)
		}
		if (generationResult != ReaderPortCommandResult.Accepted) return generationResult
		val result = synchronized(teardownOwnershipLock) {
			if (closed) return@synchronized invalidRestorationResource()
			frozenSnapshotCacheDomain = null
			restorationProgressDomain = null
			restoredTransitionSteps.clear()
			ReaderPortCommandResult.Accepted
		}
		if (result == ReaderPortCommandResult.Accepted) {
			resumeRasterGenerationAndPersistenceRequests()
			clearClosedRasterReferencesIfSettled()
		}
		return result
	}

	fun setPublicationCapacityAvailableListener(listener: () -> Unit) {
		publicationLedger.setCapacityAvailableListener(listener)
	}

	fun clearPublicationCapacityAvailableListener(listener: () -> Unit) {
		publicationLedger.clearCapacityAvailableListener(listener)
	}

	fun rasterCacheMetrics(): ReaderPageRasterCacheMetrics =
		rasterCache?.metrics()
			?: disposedRasterCacheMetrics
			?: ReaderPageRasterCacheMetrics(
				diskEntries = 0,
				diskBytes = 0L,
				diskByteLimit = 0L,
				decodedEntries = 0,
				uniqueDecodedBitmaps = 0,
				uniqueDecodedBitmapLimit = 0,
				pendingDecodedReleases = 0,
				activeEncodePins = 0,
				encodePinnedIdentities = 0
			)

	suspend fun initializeRasterCache(webView: WebView) {
		val restart = WeakReference(webView)
		withContext(Dispatchers.Main.immediate) {
			requireRasterInitializationOpen()
			rasterScheduler(webView, restart)
		}
	}

	fun ownershipMetrics(): ReaderPageTurnBundleOwnershipMetrics =
		ReaderPageTurnBundleOwnershipMetrics(
			rasterCache = rasterCacheMetrics(),
			stagedPublications = publicationLedger.entryCount(),
			stagedPublicationLimit = publicationLedger.entryLimit,
			pendingPublicationCallbacks = publicationLedger.callbackCount(),
			pendingPublicationCallbackLimit = publicationLedger.callbackLimit
		)

	fun updateBitmapQuality(quality: ReaderPageBitmapQuality): Boolean {
		if (bitmapQuality == quality) return false
		bitmapQuality = quality
		bitmapSource.updateBitmapQuality(quality)
		invalidate("bitmap-quality-${quality.persistedValue}")
		return true
	}

	fun currentGeneration(): Long = activeGeneration

	fun resolvePassiveRasterTarget(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		rasterDescriptor: ReaderPageRasterDescriptor,
		persistentOnly: Boolean = false,
		isStillCurrent: () -> Boolean,
		onResolved: (ReaderPageRasterPublicationCompletion?) -> Unit
	): ReaderPageRasterHydrationRequest {
		if (closed || frozenSnapshotCacheDomain != null) {
			onResolved(null)
			return ReaderPageRasterHydrationRequest { }
		}
		val webView = activeWebView.get()?.takeIf { it.isAttachedToWindow }
		if (
			webView == null ||
			!runCatching(isStillCurrent).getOrDefault(false)
		) {
			onResolved(null)
			return ReaderPageRasterHydrationRequest { }
		}
		val physicalLayout = readerPageRasterPhysicalLayout(reference)
		val physicalLayoutEpoch = physicalLayout?.let { layout ->
			admitPhysicalLayout(kind, layout)
		}
		if (
			physicalLayout == null ||
			physicalLayoutEpoch == null ||
			rasterDescriptor.visualPageOrdinal != pageIndex
		) {
			onResolved(null)
			return ReaderPageRasterHydrationRequest { }
		}
		cacheRasterDescriptor(
			identity = ReaderPageRasterDescriptorIdentity(
				generation = activeGeneration,
				quality = bitmapQuality,
				pageIndex = pageIndex,
				kind = kind,
				physicalLayout = physicalLayout,
				physicalLayoutEpoch = physicalLayoutEpoch,
				exactRasterIdentity = rasterDescriptor
					.key(bitmapQuality)
					.identity
					.takeIf { persistentOnly }
			),
			descriptor = rasterDescriptor
		)
		val onHydrated: (ReaderPageRasterHydrationResult?) -> Unit = { hydration ->
			val snapshot = hydration?.snapshot
			if (snapshot == null) {
				onResolved(null)
			} else if (
				hydration.durability ==
				ReaderPageRasterHydrationDurability.PersistentStoreVerified
			) {
				snapshot.release()
				onResolved(
					ReaderPageRasterPublicationCompletion(
						ReaderPageRasterPublicationResult.Durable
					)
				)
			} else {
				ensurePersistentSnapshot(
					snapshot = snapshot,
					priority = priority,
					isStillCurrent = isStillCurrent
				) { completion ->
					snapshot.release()
					onResolved(completion)
				}
			}
		}
		if (persistentOnly) {
			return registerPersistentHydration(
				webView = webView,
				pageIndex = pageIndex,
				kind = kind,
				physicalLayout = physicalLayout,
				exactDescriptor = rasterDescriptor,
				publicationFence = isStillCurrent
			) { snapshot ->
				onHydrated(
					snapshot?.let { hydrated ->
						ReaderPageRasterHydrationResult(
							snapshot = hydrated,
							durability = ReaderPageRasterHydrationDurability.PersistentStoreVerified
						)
					}
				)
			}
		}
		return hydrateSnapshotWithDurability(
			webView = webView,
			pageIndex = pageIndex,
			kind = kind,
			reference = reference,
			publicationFence = isStillCurrent,
			onHydrated = onHydrated
		)
	}

	fun admitPassiveRasterCapture(
		capture: ReaderPassiveRasterCaptureResult<Bitmap>,
		currentAuthority: ReaderPassiveRasterAdmissionAuthority,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		preparationGeneration: Long = 0L,
		isPreparationGenerationCurrent: (Long) -> Boolean = { true },
		isStillCurrent: () -> Boolean,
		onRejected: (ReaderPassiveRasterRejection) -> Unit = {},
		onPublished: (ReaderPageRasterPublicationCompletion) -> Unit
	) {
		val inputs = currentAuthority.manifestInputs
		val commit = inputs.canonicalCommit
		val descriptor = inputs.rasterDescriptor
		val failedCompletion = ReaderPageRasterPublicationCompletion(
			ReaderPageRasterPublicationResult.Failed
		)
		if (!runCatching {
				isPreparationGenerationCurrent(preparationGeneration)
			}.getOrDefault(false)
		) {
			capture.raster?.release()
			onPublished(failedCompletion)
			return
		}
		if (
			descriptor.visualPageOrdinal != inputs.visualPageOrdinal ||
			descriptor.paginationFingerprint != commit.paginationFingerprint ||
			descriptor.layoutFingerprint != commit.layoutFingerprint ||
			descriptor.decorationFingerprint != commit.decorationFingerprint
		) {
			capture.raster?.release()
			onPublished(failedCompletion)
			return
		}
		val generation = commit.rasterGeneration
		val generationIsCurrent = generation == activeGeneration
		val admitted = readerAdmitPassiveRaster(
			context = ReaderPassiveRasterAdmissionContext(
				expectedManifestSequence = capture.manifest.manifestSequence,
				currentCaptureEpoch = commit.captureEpoch,
				currentLiveFoliateSessionId = commit.liveFoliateSessionId,
				activePublicationSessionGeneration = commit.publicationSessionGeneration,
				currentDestinationCommitToken = commit.destinationCommitToken,
				currentOpaqueCaptureTarget = inputs.opaqueCaptureTarget,
				currentVisualPageOrdinal = inputs.visualPageOrdinal,
				currentRasterProfileKey = commit.rasterProfileKey,
				currentPaginationFingerprint = commit.paginationFingerprint,
				currentLayoutFingerprint = commit.layoutFingerprint,
				currentDecorationFingerprint = commit.decorationFingerprint,
				currentViewportAndCaptureGeometry = commit.viewportAndCaptureGeometry,
				currentRasterGeneration = commit.rasterGeneration,
				activePassiveSessionId = currentAuthority.activePassiveSessionId,
				expectedPassiveCommitSequence =
					currentAuthority.expectedPassiveCommitSequence,
				currentProfileAuthority = commit.profileAuthority
			),
			capture = capture
		)
		if (admitted !is ReaderPassiveRasterAdmission.Admitted) {
			onRejected((admitted as ReaderPassiveRasterAdmission.Rejected).reason)
			onPublished(failedCompletion)
			return
		}
		val publicationDescriptor = when (commit.profileAuthority) {
			ReaderPassiveRasterProfileAuthority.LiveRealized -> descriptor
			ReaderPassiveRasterProfileAuthority.PassiveRealized ->
				readerPassiveRasterReceiptAuthoritativeDescriptor(
					descriptor = descriptor,
					receipt = admitted.receipt
				)
		}
		if (
			closed ||
			pageIndex != inputs.visualPageOrdinal ||
			!generationIsCurrent ||
			!runCatching {
				isPreparationGenerationCurrent(preparationGeneration)
			}.getOrDefault(false) ||
			!runCatching(isStillCurrent).getOrDefault(false)
		) {
			admitted.releaseRaster()
			onPublished(failedCompletion)
			return
		}
		val bitmap = admitted.transferRaster()
		if (bitmap == null) {
			onPublished(failedCompletion)
			return
		}
		val snapshot = readerPassiveRasterSnapshot(
			pageIndex = pageIndex,
			kind = kind,
			bitmap = bitmap,
			reference = reference,
			captureGeometry = commit.viewportAndCaptureGeometry,
			bitmapQuality = bitmapQuality
		)
		val physicalLayout = snapshot?.let(::readerPageRasterPhysicalLayout)
		val referenceLayout = readerPageRasterPhysicalLayout(reference)
		val physicalLayoutEpoch = referenceLayout?.let { layout ->
			currentPhysicalLayoutEpoch(kind, layout)
		}
		if (
			snapshot == null ||
			physicalLayout == null ||
			referenceLayout == null ||
			!physicalLayout.matches(referenceLayout) ||
			physicalLayoutEpoch == null ||
			physicalLayoutEpoch != rasterPhysicalLayoutEpoch.get() ||
			generation != activeGeneration ||
			!runCatching {
				isPreparationGenerationCurrent(preparationGeneration)
			}.getOrDefault(false) ||
			!runCatching(isStillCurrent).getOrDefault(false)
		) {
			snapshot?.releaseCacheOwnership()
				?: bitmap.takeUnless { it.isRecycled }?.recycle()
			onPublished(failedCompletion)
			return
		}
		val exactRasterIdentity = publicationDescriptor
			.key(snapshot.key.bitmapQuality)
			.identity
			.takeIf {
				commit.profileAuthority == ReaderPassiveRasterProfileAuthority.PassiveRealized
			}
		schedulePersistentSnapshot(
			snapshot = snapshot,
			priority = priority,
			expectedDescriptor = inputs.rasterDescriptor,
			publicationDescriptor = publicationDescriptor,
			isStillCurrent = isStillCurrent
		) { completion ->
			val preparationCurrent = runCatching {
				isPreparationGenerationCurrent(preparationGeneration)
			}.getOrDefault(false)
			val publicationCurrent =
				frozenSnapshotCacheDomain == null &&
					generation == activeGeneration &&
					physicalLayoutEpoch == rasterPhysicalLayoutEpoch.get() &&
					preparationCurrent &&
					runCatching(isStillCurrent).getOrDefault(false)
			if (
				completion.result == ReaderPageRasterPublicationResult.Durable &&
				publicationCurrent
			) {
				val cached = putSnapshot(
					snapshot = snapshot,
					priority = priority,
					persist = false,
					exactRasterIdentity = exactRasterIdentity
				)
				markCachedSnapshotDurable(cached)
				onPublished(completion)
			} else {
				snapshot.releaseCacheOwnership()
				onPublished(
					if (publicationCurrent) completion
					else ReaderPageRasterPublicationCompletion(
						ReaderPageRasterPublicationResult.Failed
					)
				)
			}
		}
	}

	fun hydrationOwnerCounts(): ReaderPageRasterHydrationOwnerCounts =
		ReaderPageRasterHydrationOwnerCounts(
			descriptorRequests = descriptorRequests.size,
			descriptorRecipients = descriptorRequests.values.sumOf { it.recipients.size },
			readWorkers = hydrationScheduler.activeWorkerCount,
			readRecipients = inFlightRasterHydrations.values.sumOf { it.recipients.size }
		)

	fun protectEncodedWindow(centerPageIndex: Int, step: Int, pageCount: Int) {
		protectedEncodedCenterPageIndex = centerPageIndex
		protectedEncodedPageIndices = readerPageRasterBlockingWindow(
			centerPageIndex = centerPageIndex,
			step = step,
			pageCount = pageCount
		).toSet()
		snapshotCache.values.forEach { snapshot ->
			if (snapshot.key.visualPageIndex !in protectedEncodedPageIndices) {
				snapshotDurability[snapshot] =
					ReaderPageRasterHydrationDurability.RequiresPublication
			}
		}
		protectedEncodedProfile?.let(::stageEncodedWindowProtection)
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Encoded page window protected center=$centerPageIndex step=$step " +
				"rasters=${protectedEncodedPageIndices.size} " +
				"pages=${protectedEncodedPageIndices.sorted()} generation=$activeGeneration"
		)
	}

	private fun stageEncodedWindowProtection(profile: ReaderPageRasterProfile) {
		protectedEncodedProfile = profile
		val centerPageIndex = protectedEncodedCenterPageIndex ?: return
		if (protectedEncodedPageIndices.isEmpty()) return
		rasterCache?.stageEncodedWindowProtection(
			profile = profile,
			centerPageOrdinal = centerPageIndex,
			pinnedPageOrdinals = protectedEncodedPageIndices
		)
	}

	fun protectDecodedWindow(centerPageIndex: Int, step: Int, pageCount: Int) {
		protectDecodedPageIndices(
			readerPageSlideSnapshotWindow(
				centerPageIndex = centerPageIndex,
				step = step,
				pageCount = pageCount
			).toSet()
		)
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Decoded page window protected center=$centerPageIndex step=$step " +
				"rasters=${protectedSnapshotPageIndices.size} leaves=${protectedSnapshotPageIndices.size * step} " +
				"pages=${protectedSnapshotPageIndices.sorted()} generation=$activeGeneration"
		)
	}

	fun protectDecodedPageIndices(pageIndices: Set<Int>) {
		if (frozenSnapshotCacheDomain != null) return
		protectedSnapshotPageIndices = pageIndices.filterTo(linkedSetOf()) { it >= 0 }
		trimSnapshotCacheToCapacity()
		rasterCache?.protectDecodedPageIndices(protectedSnapshotPageIndices)
	}

	fun hasSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	): Boolean = snapshotCache.keys.any { key ->
		key.visualPageIndex == pageIndex && key.kind == kind
	}

	fun retainedReferenceSnapshot(
		preferredPageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	): ReaderPageSlideSnapshot? {
		if (closed || frozenSnapshotCacheDomain != null) return null
		return (
			cachedSnapshot(preferredPageIndex, kind)
				?: snapshotCache.entries.lastOrNull { (key, _) -> key.kind == kind }?.value
		).also { snapshot -> snapshot?.retain() }
	}

	private fun removeCachedSnapshot(
		key: ReaderPageSlideSnapshotKey,
		expected: ReaderPageSlideSnapshot? = null,
		retainTokenForRestart: Boolean = false
	): ReaderPageSlideSnapshot? {
		val cached = snapshotCache[key] ?: return null
		if (expected != null && cached !== expected) return null
		val removed = snapshotCache.remove(key) ?: return null
		snapshotDurability.remove(removed)
		snapshotExactRasterIdentities.remove(removed)
		if (!retainTokenForRestart) snapshotCacheTokens.remove(removed)
		return removed
	}

	fun trimMemory(reason: String) {
		if (frozenSnapshotCacheDomain != null) return
		val removedSnapshots = snapshotCache.entries
			.filter { (key, _) -> key.visualPageIndex !in protectedSnapshotPageIndices }
			.map { it.key to it.value }
		removedSnapshots.forEach { (key, snapshot) ->
			removeCachedSnapshot(key, snapshot)?.releaseCacheOwnership()
		}
		val removedDecoded = rasterCache?.trimDecodedToProtectedWindow() ?: 0
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Decoded working sets trimmed reason=$reason snapshots=${removedSnapshots.size} " +
				"rasters=$removedDecoded protected=${protectedSnapshotPageIndices.sorted()} " +
				"generation=$activeGeneration"
		)
	}

	fun retainedSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	): ReaderPageSlideSnapshot? {
		if (closed || frozenSnapshotCacheDomain != null) return null
		return cachedSnapshot(pageIndex, kind)?.also { snapshot -> snapshot.retain() }
	}

	fun retainedCurrentLayoutSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	): ReaderPageSlideSnapshot? = retainedCurrentLayoutSnapshot(
		pageIndex = pageIndex,
		kind = kind,
		expectedGeneration = activeGeneration,
		expectedQuality = bitmapQuality
	)

	fun retainedCurrentLayoutSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		expectedGeneration: Long,
		expectedQuality: ReaderPageBitmapQuality
	): ReaderPageSlideSnapshot? {
		if (closed || frozenSnapshotCacheDomain != null) return null
		if (
			expectedGeneration != activeGeneration ||
			expectedQuality != bitmapQuality
		) {
			return null
		}
		val authority = physicalLayoutAuthority?.takeIf { current -> current.kind == kind }
			?: return null
		return snapshotCache.entries
			.lastOrNull { (key, snapshot) ->
				key.visualPageIndex == pageIndex &&
					key.kind == kind &&
					key.bitmapQuality == expectedQuality &&
					readerPageRasterPhysicalLayout(snapshot)?.matches(authority.layout) == true
			}
			?.let { (key, value) ->
				snapshotCache[key]
				value
			}
			?.also { snapshot -> snapshot.retain() }
	}

	private fun retainedSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot
	): ReaderPageSlideSnapshot? = cachedSnapshot(pageIndex, kind, reference)
		?.also { snapshot -> snapshot.retain() }

	fun cachedSnapshotPageIndices(kind: ReaderPageTurnTransitionKind): List<Int> =
		snapshotCache.keys
			.filter { key -> key.kind == kind }
			.map { key -> key.visualPageIndex }
			.distinct()
			.sorted()

	fun captureCurrentSurface(
		webView: WebView,
		generation: Long,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) = captureCurrentSurface(webView, generation, null, onCaptured)

	fun captureCurrentSurface(
		webView: WebView,
		generation: Long,
		captureGeometry: ReaderPageTurnCaptureGeometry?,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) {
		activeWebView = WeakReference(webView)
		val publishResult: (ReaderPageTurnCaptureResult?) -> Unit = { result ->
			if (generation != activeGeneration) {
				result?.bitmap?.takeUnless { it.isRecycled }?.recycle()
				onCaptured(null)
			} else {
				onCaptured(result)
			}
		}
		if (captureGeometry == null) {
			bitmapSource.captureSurface(webView, publishResult)
		} else {
			bitmapSource.captureSurface(webView, captureGeometry, publishResult)
		}
	}

	suspend fun resolveSnapshot(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		publicationFence: () -> Boolean
	): ReaderPageSlideSnapshot? = withContext(Dispatchers.Main.immediate) {
		if (!runCatching(publicationFence).getOrDefault(false)) return@withContext null
		retainedSnapshot(pageIndex, kind, reference)?.let { retained ->
			return@withContext suspendCancellableCoroutine { continuation ->
				continuation.resume(
					retained,
					onCancellation = { _, undelivered, _ -> undelivered.release() }
				)
			}
		}
		if (!webView.isAttachedToWindow) return@withContext null
		suspendCancellableCoroutine { continuation ->
			val request = hydrateSnapshot(
				webView = webView,
				pageIndex = pageIndex,
				kind = kind,
				reference = reference,
				publicationFence = publicationFence
			) { hydrated ->
				if (continuation.isActive) {
					continuation.resume(
						hydrated,
						onCancellation = { _, undelivered, _ -> undelivered?.release() }
					)
				} else {
					hydrated?.release()
				}
			}
			continuation.invokeOnCancellation { request.cancel() }
		}
	}

	fun hydrateSnapshot(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		publicationFence: () -> Boolean = { true },
		onHydrated: (ReaderPageSlideSnapshot?) -> Unit
	): ReaderPageRasterHydrationRequest = hydrateSnapshotWithDurability(
		webView = webView,
		pageIndex = pageIndex,
		kind = kind,
		reference = reference,
		publicationFence = publicationFence
	) { result -> onHydrated(result?.snapshot) }

	fun hydrateSnapshotWithDurability(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		publicationFence: () -> Boolean = { true },
		onHydrated: (ReaderPageRasterHydrationResult?) -> Unit
	): ReaderPageRasterHydrationRequest {
		if (closed || frozenSnapshotCacheDomain != null) {
			onHydrated(null)
			return ReaderPageRasterHydrationRequest { }
		}
		activeWebView = WeakReference(webView)
		retainedSnapshot(pageIndex, kind, reference)?.let { retained ->
			deliverHydrationResult(
				callback = { snapshot ->
					onHydrated(
						snapshot?.let { hydrated ->
							ReaderPageRasterHydrationResult(
								snapshot = hydrated,
								durability = snapshotDurability[hydrated]
									?: ReaderPageRasterHydrationDurability.RequiresPublication
							)
						}
					)
				},
				snapshot = retained
			)
			return ReaderPageRasterHydrationRequest { }
		}
		if (
			!webView.isAttachedToWindow ||
			!runCatching(publicationFence).getOrDefault(false)
		) {
			onHydrated(null)
			return ReaderPageRasterHydrationRequest { }
		}
		return registerPersistentHydration(
			webView = webView,
			pageIndex = pageIndex,
			kind = kind,
			physicalLayout = readerPageRasterPhysicalLayout(reference) ?: run {
				onHydrated(null)
				return ReaderPageRasterHydrationRequest { }
			},
			publicationFence = publicationFence,
			onHydrated = { snapshot ->
				onHydrated(
					snapshot?.let { hydrated ->
						ReaderPageRasterHydrationResult(
							snapshot = hydrated,
							durability =
								ReaderPageRasterHydrationDurability.PersistentStoreVerified
						)
					}
				)
			}
		)
	}

	private fun registerPersistentHydration(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		physicalLayout: ReaderPageRasterPhysicalLayout,
		exactDescriptor: ReaderPageRasterDescriptor? = null,
		publicationFence: () -> Boolean,
		onHydrated: (ReaderPageSlideSnapshot?) -> Unit
	): ReaderPageRasterHydrationRequest {
		val recipientToken = Math.incrementExact(nextHydrationToken)
		nextHydrationToken = recipientToken
		val recipient = ReaderPageRasterHydrationRecipient(
			token = recipientToken,
			exactRasterIdentity = exactDescriptor?.key(bitmapQuality)?.identity,
			publicationFence = publicationFence,
			callback = onHydrated
		)
		val recipientOwner = synchronized(descriptorOwnershipAdmissionLock) {
			descriptorRequestOwnership.admit(
				ReaderExactPhysicalOwnerDescriptor(
					kind = ReaderTransitionResourceKind.CallbackRegistration,
					origin = ReaderLegacyResourceOrigin.Pending,
					state = ReaderLegacyResourceState.Registered
				),
				cancelPhysical = {
					dispatchToMain { cancelHydrationRecipient(recipientToken) }
					true
				}
			)?.also { owner ->
				recipient.descriptorPhysicalOwner = owner
				descriptorPhysicalRecipients[recipientToken] = recipient
			}
		} ?: run {
			onHydrated(null)
			return ReaderPageRasterHydrationRequest { }
		}
		val physicalLayoutEpoch = admitPhysicalLayout(kind, physicalLayout) ?: run {
			completeDescriptorRecipient(recipient)
			onHydrated(null)
			return ReaderPageRasterHydrationRequest { }
		}
		val identity = ReaderPageRasterDescriptorIdentity(
			generation = activeGeneration,
			quality = bitmapQuality,
			pageIndex = pageIndex,
			kind = kind,
			physicalLayout = physicalLayout,
			physicalLayoutEpoch = physicalLayoutEpoch,
			exactRasterIdentity = exactDescriptor?.key(bitmapQuality)?.identity
		)
		val existing = synchronized(descriptorOwnershipAdmissionLock) {
			if (descriptorPhysicalRecipients[recipientToken] !== recipient) {
				return@synchronized null
			}
			descriptorRequestTokens[identity]
				?.let(descriptorRequests::get)
				?.also { request -> request.recipients[recipientToken] = recipient }
		}
		if (existing == null) {
			if (synchronized(descriptorOwnershipAdmissionLock) {
					descriptorPhysicalRecipients[recipientToken] !== recipient
				}) {
				return ReaderPageRasterHydrationRequest { }
			}
			val descriptorToken = Math.incrementExact(nextHydrationToken)
			nextHydrationToken = descriptorToken
			val requestOwner = synchronized(descriptorOwnershipAdmissionLock) {
				descriptorRequestOwnership.admit(
					ReaderExactPhysicalOwnerDescriptor(
						kind = ReaderTransitionResourceKind.Raster,
						origin = ReaderLegacyResourceOrigin.Pending,
						state = ReaderLegacyResourceState.Reserved
					),
					cancelPhysical = {
						dispatchToMain { cancelDescriptorRequest(descriptorToken) }
						true
					}
				)
			} ?: run {
				completeDescriptorRecipient(recipient)
				onHydrated(null)
				return ReaderPageRasterHydrationRequest { }
			}
			val request = ReaderPageRasterDescriptorRequest(
				token = descriptorToken,
				identity = identity,
				webView = WeakReference(webView),
				recipients = linkedMapOf(recipientToken to recipient),
				physicalOwner = requestOwner
			)
			val published = synchronized(descriptorOwnershipAdmissionLock) {
				if (descriptorPhysicalRecipients[recipientToken] !== recipient) {
					false
				} else {
					descriptorRequests[descriptorToken] = request
					descriptorRequestTokens[identity] = descriptorToken
					true
				}
			}
			if (!published) {
				descriptorRequestOwnership.complete(requestOwner)
				return ReaderPageRasterHydrationRequest { }
			}
			val cached = rasterDescriptors[identity] ?: exactDescriptor
			if (cached != null) {
				dispatchRasterDescriptor(descriptorToken, cached)
			} else {
				try {
					descriptorPort.request(webView, pageIndex) { descriptor ->
						dispatchRasterDescriptor(descriptorToken, descriptor)
					}
				} catch (_: Throwable) {
					failDescriptorRequest(descriptorToken)
				}
			}
		}
		checkNotNull(recipientOwner)
		return ReaderPageRasterHydrationRequest {
			dispatchToMain { cancelHydrationRecipient(recipientToken) }
		}
	}

	private fun completeDescriptorRecipient(
		recipient: ReaderPageRasterHydrationRecipient
	) {
		val owner = synchronized(descriptorOwnershipAdmissionLock) {
			if (descriptorPhysicalRecipients[recipient.token] === recipient) {
				descriptorPhysicalRecipients.remove(recipient.token)
			}
			recipient.descriptorPhysicalOwner.also {
				recipient.descriptorPhysicalOwner = null
			}
		}
		owner?.let(descriptorRequestOwnership::complete)
	}

	private fun completeDescriptorRequest(request: ReaderPageRasterDescriptorRequest) {
		descriptorRequestOwnership.complete(request.physicalOwner)
	}

	private fun cancelDescriptorRequest(requestToken: Long) {
		val request = synchronized(descriptorOwnershipAdmissionLock) {
			val removed = descriptorRequests.remove(requestToken) ?: return@synchronized null
			descriptorRequestTokens[removed.identity]
				?.takeIf { token -> token == requestToken }
				?.let { descriptorRequestTokens.remove(removed.identity) }
			removed
		} ?: return
		request.recipients.values.forEach(::completeDescriptorRecipient)
		completeDescriptorRequest(request)
	}

	private fun takeHydrationRecipientForFinalization(
		hydration: InFlightRasterHydration,
		recipient: ReaderPageRasterHydrationRecipient
	): Boolean = synchronized(hydrationOwnershipAdmissionLock) {
		inFlightRasterHydrations[hydration.identity] === hydration &&
			hydration.recipients.remove(recipient.token) === recipient
	}

	private fun completeHydrationRecipient(
		recipient: ReaderPageRasterHydrationRecipient
	) {
		val owner = synchronized(hydrationOwnershipAdmissionLock) {
			recipient.hydrationPhysicalOwner.also {
				recipient.hydrationPhysicalOwner = null
			}
		}
		owner?.let(hydrationOwnership::complete)
	}

	private fun completeHydration(hydration: InFlightRasterHydration) {
		hydrationOwnership.complete(hydration.physicalOwner)
	}

	private fun cancelHydrationWorker(hydrationToken: Long) {
		var unscheduled: InFlightRasterHydration? = null
		val job = synchronized(hydrationOwnershipAdmissionLock) {
			val matched = inFlightRasterHydrations.values.firstOrNull {
				it.token == hydrationToken
			} ?: return@synchronized null
			matched.job ?: run {
				if (inFlightRasterHydrations[matched.identity] === matched) {
					inFlightRasterHydrations.remove(matched.identity)
				}
				unscheduled = matched
				null
			}
		}
		if (job != null) {
			job.cancel()
			return
		}
		unscheduled?.let { hydration ->
			hydration.recipients.values.forEach(::completeHydrationRecipient)
			completeHydration(hydration)
		}
	}

	private fun cacheRasterDescriptor(
		identity: ReaderPageRasterDescriptorIdentity,
		descriptor: ReaderPageRasterDescriptor
	) {
		if (
			identity.generation != activeGeneration ||
			identity.quality != bitmapQuality ||
			identity.physicalLayoutEpoch != rasterPhysicalLayoutEpoch.get() ||
			descriptor.visualPageOrdinal != identity.pageIndex ||
			identity.exactRasterIdentity?.let { exact ->
				descriptor.key(identity.quality).identity != exact
			} == true
		) {
			return
		}
		rasterDescriptors[identity] = descriptor
		while (rasterDescriptors.size > MaxCachedRasterDescriptors) {
			rasterDescriptors.remove(rasterDescriptors.keys.first())
		}
	}

	private fun dispatchRasterDescriptor(
		requestToken: Long,
		descriptor: ReaderPageRasterDescriptor?
	) = dispatchToMain {
		val request = synchronized(descriptorOwnershipAdmissionLock) {
			val removed = descriptorRequests.remove(requestToken)
				?.takeIf { candidate -> candidate.token == requestToken }
				?: return@synchronized null
			descriptorRequestTokens[removed.identity]
				?.takeIf { token -> token == requestToken }
				?.let { descriptorRequestTokens.remove(removed.identity) }
			removed
		} ?: return@dispatchToMain
		completeDescriptorRequest(request)
		val recipients = request.recipients.values.toList()
		val webView = request.webView.get()
		val key = descriptor?.key(request.identity.quality)
		if (
			descriptor == null ||
			key == null ||
			descriptor.visualPageOrdinal != request.identity.pageIndex ||
			request.identity.exactRasterIdentity?.let { exact ->
				key.identity != exact
			} == true ||
			closed ||
			request.identity.generation != activeGeneration ||
			request.identity.quality != bitmapQuality ||
			request.identity.physicalLayoutEpoch != rasterPhysicalLayoutEpoch.get() ||
			webView?.isAttachedToWindow != true
		) {
			recipients.forEach { recipient ->
				try {
					deliverHydrationResult(recipient.callback, null)
				} finally {
					completeDescriptorRecipient(recipient)
				}
			}
			return@dispatchToMain
		}
		val currentRecipients = recipients.filter { recipient ->
			runCatching(recipient.publicationFence).getOrDefault(false)
		}
		(recipients - currentRecipients.toSet()).forEach { recipient ->
			try {
				deliverHydrationResult(recipient.callback, null)
			} finally {
				completeDescriptorRecipient(recipient)
			}
		}
		if (currentRecipients.isEmpty()) return@dispatchToMain
		val hydrationIdentity = ReaderPageRasterHydrationIdentity(
			rasterIdentity = key.identity,
			kind = request.identity.kind,
			physicalLayout = request.identity.physicalLayout,
			physicalLayoutEpoch = request.identity.physicalLayoutEpoch
		)
		val existingHydration = synchronized(hydrationOwnershipAdmissionLock) {
			inFlightRasterHydrations[hydrationIdentity]
				?.takeIf { hydration ->
					hydration.generation == request.identity.generation &&
						hydration.quality == request.identity.quality
				}
		}
		if (existingHydration != null) {
			val admitted = synchronized(hydrationOwnershipAdmissionLock) {
				currentRecipients.filter { recipient ->
					val owner = hydrationOwnership.admit(
						ReaderExactPhysicalOwnerDescriptor(
							kind = ReaderTransitionResourceKind.CallbackRegistration,
							origin = ReaderLegacyResourceOrigin.Pending,
							state = ReaderLegacyResourceState.Registered
						),
						cancelPhysical = {
							dispatchToMain { cancelHydrationRecipient(recipient.token) }
							true
						}
					) ?: return@filter false
					recipient.hydrationPhysicalOwner = owner
					existingHydration.recipients[recipient.token] = recipient
					true
				}
			}
			admitted.forEach(::completeDescriptorRecipient)
			(currentRecipients - admitted.toSet()).forEach { recipient ->
				try {
					deliverHydrationResult(recipient.callback, null)
				} finally {
					completeDescriptorRecipient(recipient)
				}
			}
			if (admitted.isNotEmpty()) {
				cacheRasterDescriptor(request.identity, descriptor)
				stageEncodedWindowProtection(key.profile)
			}
			return@dispatchToMain
		}
		val hydrationToken = Math.incrementExact(nextHydrationToken)
		nextHydrationToken = hydrationToken
		val hydration = synchronized(hydrationOwnershipAdmissionLock) {
			val hydrationOwner = hydrationOwnership.admit(
				ReaderExactPhysicalOwnerDescriptor(
					kind = ReaderTransitionResourceKind.Raster,
					state = ReaderLegacyResourceState.Running
				),
				cancelPhysical = {
					dispatchToMain { cancelHydrationWorker(hydrationToken) }
					true
				}
			) ?: return@synchronized null
			val admittedRecipients = currentRecipients.map { recipient ->
				val owner = checkNotNull(
					hydrationOwnership.admit(
						ReaderExactPhysicalOwnerDescriptor(
							kind = ReaderTransitionResourceKind.CallbackRegistration,
							origin = ReaderLegacyResourceOrigin.Pending,
							state = ReaderLegacyResourceState.Registered
						),
						cancelPhysical = {
							dispatchToMain { cancelHydrationRecipient(recipient.token) }
							true
						}
					)
				)
				recipient.hydrationPhysicalOwner = owner
				recipient
			}
			InFlightRasterHydration(
				token = hydrationToken,
				identity = hydrationIdentity,
				generation = request.identity.generation,
				quality = request.identity.quality,
				key = key,
				kind = request.identity.kind,
				webView = WeakReference(webView),
				recipients = admittedRecipients.associateByTo(linkedMapOf()) { it.token },
				physicalOwner = hydrationOwner
			).also { inFlightRasterHydrations[hydrationIdentity] = it }
		}
		if (hydration == null) {
			currentRecipients.forEach { recipient ->
				try {
					deliverHydrationResult(recipient.callback, null)
				} finally {
					completeDescriptorRecipient(recipient)
				}
			}
			return@dispatchToMain
		}
		currentRecipients.forEach(::completeDescriptorRecipient)
		cacheRasterDescriptor(request.identity, descriptor)
		stageEncodedWindowProtection(key.profile)
		val job = hydrationScheduler.schedule { runPersistentHydration(hydration) }
		if (job == null) {
			val removed = synchronized(hydrationOwnershipAdmissionLock) {
				if (inFlightRasterHydrations[hydrationIdentity] === hydration) {
					inFlightRasterHydrations.remove(hydrationIdentity)
					hydration
				} else {
					null
				}
			}
			removed?.let { failed ->
				failed.recipients.values.forEach { recipient ->
					try {
						deliverHydrationResult(recipient.callback, null)
					} finally {
						completeHydrationRecipient(recipient)
					}
				}
				completeHydration(failed)
			}
		} else {
			val attached = synchronized(hydrationOwnershipAdmissionLock) {
				if (inFlightRasterHydrations[hydrationIdentity] === hydration) {
					hydration.job = job
					true
				} else {
					false
				}
			}
			if (!attached) job.cancel()
		}
	}

	private fun failDescriptorRequest(requestToken: Long) = dispatchToMain {
		val request = synchronized(descriptorOwnershipAdmissionLock) {
			val removed = descriptorRequests.remove(requestToken) ?: return@synchronized null
			descriptorRequestTokens[removed.identity]
				?.takeIf { token -> token == requestToken }
				?.let { descriptorRequestTokens.remove(removed.identity) }
			removed
		} ?: return@dispatchToMain
		completeDescriptorRequest(request)
		request.recipients.values.forEach { recipient ->
			try {
				deliverHydrationResult(recipient.callback, null)
			} finally {
				completeDescriptorRecipient(recipient)
			}
		}
	}

	private suspend fun runPersistentHydration(
		hydration: InFlightRasterHydration
	) {
		var raster: ReaderPageRaster<Bitmap>? = null
		var rasterOwnershipTransferred = false
		try {
			if (!isHydrationCurrent(hydration)) return
			val webView = hydration.webView.get() ?: return
			raster = readPersistentRaster(webView, hydration.key)
			if (!isHydrationCurrent(hydration)) return
			val value = raster ?: return
			if (value.key.identity != hydration.key.identity) return
			val bitmap = value.value
			val leafGeometry = readerPageRasterLeafGeometry(
				metadata = value.metadata,
				bitmapWidth = bitmap.width,
				bitmapHeight = bitmap.height
			)
			val physicalLayout = hydration.identity.physicalLayout
			val surface = physicalLayout.surfaceRectInWindow
			val surfaceRectInWindow = Rect(
				surface.left,
				surface.top,
				surface.right,
				surface.bottom
			)
			val kindMatches = readerPageRasterGeometryMatches(
				hydration.kind,
				leafGeometry
			)
			val physicalLayoutMatches = leafGeometry?.let { geometry ->
				readerPageRasterPhysicalLayout(
					surfaceRectInWindow = surfaceRectInWindow,
					bitmapWidth = bitmap.width,
					bitmapHeight = bitmap.height,
					geometry = geometry
				)?.matches(physicalLayout)
			} == true
			if (!kindMatches || !physicalLayoutMatches) {
				if (!kindMatches) {
					runCatching { removePersistentRaster(hydration.key, value.metadata) }
				}
				Logger.w(
					ReaderPageTurnBundleSourceTag,
					"Discarded incompatible page raster page=${hydration.key.visualPageOrdinal} " +
						"kind=${hydration.kind} key=${hydration.key.digest}"
				)
				return
			}
			withContext(Dispatchers.Main.immediate) {
				val recipients = synchronized(hydrationOwnershipAdmissionLock) {
					if (inFlightRasterHydrations[hydration.identity] !== hydration) {
						return@synchronized null
					}
					hydration.recipients.values.toList()
				} ?: return@withContext
				val sourceCurrent = !closed &&
					frozenSnapshotCacheDomain == null &&
					hydration.generation == activeGeneration &&
					hydration.quality == bitmapQuality &&
					hydration.identity.physicalLayoutEpoch == rasterPhysicalLayoutEpoch.get() &&
					hydration.webView.get()?.isAttachedToWindow == true
				val eligible = recipients.filter { recipient ->
					sourceCurrent &&
						runCatching(recipient.publicationFence).getOrDefault(false)
				}
				(recipients - eligible.toSet()).forEach { recipient ->
					if (!takeHydrationRecipientForFinalization(hydration, recipient)) {
						return@forEach
					}
					try {
						deliverHydrationResult(recipient.callback, null)
					} finally {
						completeHydrationRecipient(recipient)
					}
				}
				if (
					eligible.isEmpty() ||
					frozenSnapshotCacheDomain != null ||
					!isHydrationCurrent(hydration)
				) {
					return@withContext
				}
				val exactRasterIdentities = eligible.asSequence()
					.mapNotNull { recipient -> recipient.exactRasterIdentity }
					.distinct()
					.toList()
				check(
					exactRasterIdentities.size <= 1 &&
						exactRasterIdentities.all { identity ->
							identity == hydration.key.identity
						}
				) { "Exact hydration recipient identity did not match its raster key" }
				val exactRasterIdentity = exactRasterIdentities.singleOrNull()
				val snapshot = ReaderPageSlideSnapshot(
					key = snapshotKey(
						hydration.key.visualPageOrdinal,
						hydration.kind,
						bitmap,
						surfaceRectInWindow
					),
					bitmap = bitmap,
					surfaceRectInWindow = Rect(surfaceRectInWindow),
					leafGeometry = checkNotNull(leafGeometry),
					reverseFaceColor = value.metadata.reverseFaceColor
				)
				rasterOwnershipTransferred = true
				val cached = try {
					putSnapshot(
						snapshot = snapshot,
						priority = ReaderPageRasterPriority.NextTransition,
						persist = false,
						exactRasterIdentity = exactRasterIdentity,
						recipientRetainCount = eligible.size
					)
				} catch (failure: Throwable) {
					snapshot.releaseCacheOwnership()
					throw failure
				}
				var pendingRecipientRetains = eligible.size
				try {
					markCachedSnapshotDurable(cached)
					val admittedRecipients = eligible.filter { recipient ->
						if (takeHydrationRecipientForFinalization(hydration, recipient)) {
							true
						} else {
							pendingRecipientRetains -= 1
							cached.release()
							false
						}
					}
					admittedRecipients.forEach { recipient ->
						pendingRecipientRetains -= 1
						try {
							deliverHydrationResult(recipient.callback, cached)
						} finally {
							completeHydrationRecipient(recipient)
						}
					}
				} finally {
					repeat(pendingRecipientRetains) { cached.release() }
				}
			}
		} finally {
			if (!rasterOwnershipTransferred) {
				raster?.value?.let(ReaderAndroidPageRasterCodec::release)
			}
			withContext(NonCancellable + Dispatchers.Main.immediate) {
				val remaining = synchronized(hydrationOwnershipAdmissionLock) {
					if (inFlightRasterHydrations[hydration.identity] === hydration) {
						inFlightRasterHydrations.remove(hydration.identity)
						hydration.recipients.values.toList().also {
							hydration.recipients.clear()
						}
					} else {
						emptyList()
					}
				}
				remaining.forEach { recipient ->
					try {
						deliverHydrationResult(recipient.callback, null)
					} finally {
						completeHydrationRecipient(recipient)
					}
				}
				completeHydration(hydration)
			}
		}
	}

	private suspend fun readPersistentRaster(
		webView: WebView,
		key: ReaderPageRasterKey
	): ReaderPageRaster<Bitmap>? {
		val injectedStore = hydrationStorePort
		val productionStore = if (injectedStore == null) {
			val scheduler = rasterScheduler(webView)
			scheduler.activateProfile(key.profile)
			persistentStore
		} else {
			null
		}
		var result: ReaderPageRaster<Bitmap>? = null
		var readFailure: Throwable? = null
		try {
			withContext(NonCancellable + Dispatchers.IO) {
				try {
					result = if (injectedStore != null) {
						injectedStore.readCopy(key)
					} else {
						productionStore?.readCopy(key) { cached ->
							cached.copy(Bitmap.Config.ARGB_8888, false)
						}
					}
				} catch (failure: Throwable) {
					readFailure = failure
				}
			}
		} catch (_: CancellationException) {
			// The non-cancellable read already captured ownership or failure.
		} catch (failure: Throwable) {
			readFailure = failure
		}
		readFailure?.let { failure -> throw failure }
		return result
	}

	private suspend fun removePersistentRaster(
		key: ReaderPageRasterKey,
		expectedMetadata: ReaderPageRasterMetadata
	): Boolean {
		hydrationStorePort?.let { store -> return store.remove(key, expectedMetadata) }
		val store = persistentStore ?: return false
		return withContext(Dispatchers.IO) { store.remove(key, expectedMetadata) }
	}

	private fun isHydrationCurrent(hydration: InFlightRasterHydration): Boolean =
		!closed &&
			hydration.generation == activeGeneration &&
			hydration.quality == bitmapQuality &&
			hydration.identity.physicalLayoutEpoch == rasterPhysicalLayoutEpoch.get() &&
			hydration.webView.get()?.isAttachedToWindow == true &&
			synchronized(hydrationOwnershipAdmissionLock) {
				inFlightRasterHydrations[hydration.identity] === hydration
			}

	private fun cancelHydrationRecipient(recipientToken: Long) {
		var descriptorRecipient: ReaderPageRasterHydrationRecipient? = null
		var emptyDescriptorRequest: ReaderPageRasterDescriptorRequest? = null
		synchronized(descriptorOwnershipAdmissionLock) {
			descriptorRequests.values.firstOrNull { request ->
				recipientToken in request.recipients
			}?.let { request ->
				descriptorRecipient = request.recipients.remove(recipientToken)
				if (request.recipients.isEmpty()) {
					descriptorRequests.remove(request.token)
					descriptorRequestTokens[request.identity]
						?.takeIf { token -> token == request.token }
						?.let { descriptorRequestTokens.remove(request.identity) }
					emptyDescriptorRequest = request
				}
			}
			if (descriptorRecipient == null) {
				descriptorRecipient = descriptorPhysicalRecipients[recipientToken]
			}
		}
		descriptorRecipient?.let(::completeDescriptorRecipient)
		emptyDescriptorRequest?.let(::completeDescriptorRequest)
		if (descriptorRecipient != null) return
		var hydrationRecipient: ReaderPageRasterHydrationRecipient? = null
		var emptyHydration: InFlightRasterHydration? = null
		val hydrationJob = synchronized(hydrationOwnershipAdmissionLock) {
			inFlightRasterHydrations.values.firstOrNull { hydration ->
				recipientToken in hydration.recipients
			}?.let { hydration ->
				hydrationRecipient = hydration.recipients.remove(recipientToken)
				if (hydration.recipients.isEmpty()) {
					inFlightRasterHydrations.remove(hydration.identity)
					emptyHydration = hydration
					hydration.job
				} else {
					null
				}
			}
		}
		hydrationRecipient?.let(::completeHydrationRecipient)
		emptyHydration?.let { hydration ->
			if (hydrationJob == null) completeHydration(hydration)
			else hydrationJob.cancel()
		}
	}

	private fun deliverHydrationResult(
		callback: (ReaderPageSlideSnapshot?) -> Unit,
		snapshot: ReaderPageSlideSnapshot?
	) {
		try {
			callback(snapshot)
		} catch (_: Throwable) {
			snapshot?.release()
		}
	}

	private fun dispatchToMain(action: () -> Unit) {
		if (Looper.myLooper() == Looper.getMainLooper()) action()
		else mainHandler.post(action)
	}

	private fun registerLiveValidation(
		handle: ReaderPageRelocationContentValidationHandle
	): ReaderPageLiveValidationCaptureStage? = synchronized(liveValidationAdmissionLock) {
		synchronized(closeFenceLock) {
			if (closed) return@synchronized null
			val stage = ReaderPageLiveValidationCaptureStage(
				ownership = liveValidationOwnership,
				cancelPhysical = handle::cancel,
				onOwnershipMutated = onOwnershipMutated
			)
			val physicalOwners = liveValidationOwnership.admit(
				listOf(
					ReaderExactPhysicalOwnerDescriptor(
						kind = ReaderTransitionResourceKind.Raster,
						state = ReaderLegacyResourceState.Running
					),
					ReaderExactPhysicalOwnerDescriptor(
						kind = ReaderTransitionResourceKind.CallbackRegistration,
						origin = ReaderLegacyResourceOrigin.Pending,
						state = ReaderLegacyResourceState.Registered
					)
				),
				cancelPhysical = stage::cancel
			) ?: return@synchronized null
			stage.attach(physicalOwners)
			activeLiveValidations.add(handle)
			stage
		}
	}

	private fun unregisterLiveValidation(
		handle: ReaderPageRelocationContentValidationHandle
	) {
		synchronized(closeFenceLock) {
			activeLiveValidations.remove(handle)
		}
	}

	private fun postLiveValidationResult(
		ownership: ReaderPageLiveValidationSnapshotOwnership<
			ReaderPageSlideSnapshot,
			ReaderPageTurnLiveCaptureResult
		>,
		webView: WebView,
		target: ReaderPageTurnPresentationTarget.Live,
		acceptedReceipt: ReaderPageTurnPresentationReceipt?,
		isStillCurrent: () -> Boolean,
		onValidated: (ReaderPageRelocationContentValidationResult) -> Unit
	) {
		lateinit var stage: ReaderPageLiveValidationCallbackStage
		val publication = Runnable {
			stage.run publication@{
				val workerResult = ownership.awaitingResult() ?: return@publication
				val current = synchronized(closeFenceLock) { !closed } &&
					runCatching(isStillCurrent).getOrDefault(false)
				val receipt = acceptedReceipt
				if (
					workerResult != ReaderPageRelocationContentValidationResult.Accepted ||
					!current ||
					receipt == null
				) {
					ownership.publish { _, _, _, completed ->
						onValidated(
							if (current) completed
							else ReaderPageRelocationContentValidationResult.Invalidated
						)
					}
					return@publication
				}
				var finalFence: ReaderPageRelocationContentValidationHandle? = null
				val finalStage = ReaderPageLiveValidationCallbackStage(
					ownership = liveValidationOwnership,
					cancelExternal = { finalFence?.cancel() },
					onCancelled = { ownership.cancel() },
					onOwnershipMutated = onOwnershipMutated
				)
				val finalOwner = synchronized(liveValidationAdmissionLock) {
					liveValidationOwnership.admit(
						ReaderExactPhysicalOwnerDescriptor(
							kind = ReaderTransitionResourceKind.CallbackRegistration,
							origin = ReaderLegacyResourceOrigin.Pending,
							state = ReaderLegacyResourceState.Registered
						),
						cancelPhysical = finalStage::cancel
					)
				}
				if (finalOwner == null) {
					ownership.cancel()
					return@publication
				}
				finalStage.attach(finalOwner)
				onOwnershipMutated()
				finalFence = try {
					bitmapSource.confirmLivePresentationReceipt(
						webView = webView,
						target = target,
						acceptedReceipt = receipt,
						isStillCurrent = {
							synchronized(closeFenceLock) { !closed } &&
								runCatching(isStillCurrent).getOrDefault(false)
						}
					) { currentReceipt ->
						finalStage.run {
							ownership.publish { _, _, _, completed ->
								val publicationCurrent = synchronized(closeFenceLock) { !closed } &&
									runCatching(isStillCurrent).getOrDefault(false)
								onValidated(
									readerPageLiveValidationReceiptFencedResult(
										workerResult = completed,
										target = target,
										acceptedReceipt = receipt,
										currentReceipt = currentReceipt,
										isStillCurrent = publicationCurrent
									)
								)
							}
						}
					}
				} catch (_: Throwable) {
					finalStage.run { ownership.cancel() }
					null
				}
				finalFence?.let(ownership::attachFinalFence)
			}
		}
		stage = ReaderPageLiveValidationCallbackStage(
			ownership = liveValidationOwnership,
			cancelExternal = { mainHandler.removeCallbacks(publication) },
			onCancelled = { ownership.cancel() },
			onOwnershipMutated = onOwnershipMutated
		)
		val physicalOwner = synchronized(liveValidationAdmissionLock) {
			liveValidationOwnership.admit(
				ReaderExactPhysicalOwnerDescriptor(
					kind = ReaderTransitionResourceKind.CallbackRegistration,
					origin = ReaderLegacyResourceOrigin.Pending,
					state = ReaderLegacyResourceState.Registered
				),
				cancelPhysical = stage::cancel
			)
		}
		if (physicalOwner == null) {
			ownership.cancel()
			return
		}
		stage.attach(physicalOwner)
		onOwnershipMutated()
		if (!mainHandler.post(publication)) {
			stage.run { ownership.cancel() }
		}
	}

	fun validateLivePresentation(
		webView: WebView,
		rendererSurface: PageSurfaceView,
		request: ReaderPageRelocationRequest,
		foregroundMutationGeneration: ReaderForegroundWebViewMutationGeneration,
		expectedTarget: ReaderPageSlideSnapshot,
		expectedSource: ReaderPageSlideSnapshot?,
		isStillCurrent: () -> Boolean,
		onValidated: (ReaderPageRelocationContentValidationResult) -> Unit
	): ReaderPageRelocationContentValidationHandle {
		val validationGeneration = synchronized(closeFenceLock) { activeGeneration }
		val expectedTargetBitmapWidth = expectedTarget.bitmap.width
		val expectedTargetBitmapHeight = expectedTarget.bitmap.height
		fun validationIsCurrent(): Boolean {
			val callerCurrent = runCatching(isStillCurrent).getOrDefault(false)
			return synchronized(closeFenceLock) {
				readerPageLiveValidationIsCurrent(
					expectedGeneration = validationGeneration,
					currentGeneration = activeGeneration,
					closed = closed,
					callerCurrent = callerCurrent
				)
			}
		}
		fun liveValidationGenerationIsCurrent(): Boolean = synchronized(closeFenceLock) {
			readerPageLiveValidationIsCurrent(
				expectedGeneration = validationGeneration,
				currentGeneration = activeGeneration,
				closed = closed,
				callerCurrent = true
			)
		}
		lateinit var ownership: ReaderPageLiveValidationSnapshotOwnership<
			ReaderPageSlideSnapshot,
			ReaderPageTurnLiveCaptureResult
		>
		var capturePhysicalStage: ReaderPageLiveValidationCaptureStage? = null
		ownership = ReaderPageLiveValidationSnapshotOwnership(
			expectedTarget = expectedTarget,
			expectedSource = expectedSource,
			releaseExpected = ReaderPageSlideSnapshot::release,
			releaseCandidate = { captured ->
				captured.captured.bitmap.takeUnless { bitmap -> bitmap.isRecycled }?.recycle()
			},
			onTerminal = { unregisterLiveValidation(ownership) }
		)
		fun completeImmediately(
			result: ReaderPageRelocationContentValidationResult
		): Boolean {
			capturePhysicalStage?.complete()
			if (!ownership.completeCapture(candidate = null, result = result)) return false
			return ownership.publish { _, _, _, completed -> onValidated(completed) }
		}
		capturePhysicalStage = registerLiveValidation(ownership)
		if (capturePhysicalStage == null) {
			completeImmediately(ReaderPageRelocationContentValidationResult.Invalidated)
			return ReaderPageRelocationContentValidationHandle.Completed
		}
		if (!validationIsCurrent()) {
			completeImmediately(ReaderPageRelocationContentValidationResult.Invalidated)
			return ReaderPageRelocationContentValidationHandle.Completed
		}
		val target = runCatching {
			ReaderPageTurnPresentationTarget.Live(
				token = request.token.value,
				pageIndex = request.destinationOrdinal.toLong(),
				foliateSessionId = request.foliateSessionId,
				rasterGeneration = request.rasterGeneration,
				textureGeneration = request.textureGeneration,
				foregroundMutationGeneration = foregroundMutationGeneration.value
			)
		}.getOrNull()
		if (target == null) {
			completeImmediately(ReaderPageRelocationContentValidationResult.Invalidated)
			return ReaderPageRelocationContentValidationHandle.Completed
		}
		val captureHandle = try {
			bitmapSource.captureLiveCompositedSurface(
				webView = webView,
				rendererSurface = rendererSurface,
				target = target,
				expectedBitmapWidth = expectedTargetBitmapWidth,
				expectedBitmapHeight = expectedTargetBitmapHeight,
				isStillCurrent = ::validationIsCurrent
			) { captured ->
				capturePhysicalStage.complete()
				if (captured == null) {
					Logger.i(
						ReaderPageTurnBundleSourceTag,
						"Live page-turn validation capture result=Unavailable"
					)
					if (
						ownership.completeCapture(
							candidate = null,
							result = ReaderPageRelocationContentValidationResult.ContentRejected
						)
					) {
						postLiveValidationResult(
							ownership = ownership,
							webView = webView,
							target = target,
							acceptedReceipt = null,
							isStillCurrent = ::validationIsCurrent,
							onValidated = onValidated
						)
					}
					return@captureLiveCompositedSurface
				}
				if (!validationIsCurrent()) {
					if (
						ownership.completeCapture(
							candidate = captured,
							result = ReaderPageRelocationContentValidationResult.Invalidated
						)
					) {
						postLiveValidationResult(
							ownership = ownership,
							webView = webView,
							target = target,
							acceptedReceipt = captured.acceptedReceipt,
							isStillCurrent = ::validationIsCurrent,
							onValidated = onValidated
						)
					}
					return@captureLiveCompositedSurface
				}
				val work = ownership.beginWorker(captured)
					?: return@captureLiveCompositedSurface
				lateinit var workerJob: Job
				val workerStage = ReaderPageLiveValidationCaptureStage(
					ownership = liveValidationOwnership,
					cancelPhysical = {
						workerJob.cancel()
						true
					},
					onOwnershipMutated = onOwnershipMutated
				)
				val workerOwner = try {
					synchronized(liveValidationAdmissionLock) {
						val admitted = liveValidationOwnership.admit(
							ReaderExactPhysicalOwnerDescriptor(
								kind = ReaderTransitionResourceKind.Raster,
								state = ReaderLegacyResourceState.Running
							),
							cancelPhysical = workerStage::cancel
						) ?: return@synchronized null
						workerStage.attach(listOf(admitted))
						workerJob = rasterScope.launch(
							context = liveValidationDispatcher,
							start = CoroutineStart.LAZY
						) {
							val workerContext = currentCoroutineContext()
							val result = try {
								workerContext.ensureActive()
								if (!liveValidationGenerationIsCurrent()) {
									ReaderPageRelocationContentValidationResult.Invalidated
								} else {
									val compared = readerPageLiveCaptureValidationResult(
										candidate = work.candidate,
										expectedTarget = work.expectedTarget,
										expectedSource = work.expectedSource,
										cancellationCheck = { workerContext.ensureActive() }
									)
									Logger.i(
										ReaderPageTurnBundleSourceTag,
										"Live page-turn validation comparison result=$compared"
									)
									if (liveValidationGenerationIsCurrent()) compared
									else ReaderPageRelocationContentValidationResult.Invalidated
								}
							} catch (cancelled: CancellationException) {
								throw cancelled
							} catch (_: Throwable) {
								ReaderPageRelocationContentValidationResult.Invalidated
							}
							ownership.recordWorkerResult(result)
						}
						admitted
					}
				} catch (_: Throwable) {
					ownership.recordWorkerResult(
						ReaderPageRelocationContentValidationResult.Invalidated
					)
					try {
						if (ownership.workerFinished(cancelled = false)) {
							postLiveValidationResult(
								ownership = ownership,
								webView = webView,
								target = target,
								acceptedReceipt = work.candidate.acceptedReceipt,
								isStillCurrent = ::validationIsCurrent,
								onValidated = onValidated
							)
						}
					} finally {
						workerStage.complete()
					}
					return@captureLiveCompositedSurface
				}
				if (workerOwner == null) {
					ownership.recordWorkerResult(
						ReaderPageRelocationContentValidationResult.Invalidated
					)
					ownership.workerFinished(cancelled = true)
					return@captureLiveCompositedSurface
				}
				onOwnershipMutated()
				ownership.attachWorker(
					ReaderPageRelocationContentValidationHandle {
						workerJob.cancel()
						true
					}
				)
				workerJob.invokeOnCompletion { failure ->
					try {
						if (
							ownership.workerFinished(
								cancelled = failure is CancellationException
							)
						) {
							postLiveValidationResult(
								ownership = ownership,
								webView = webView,
								target = target,
								acceptedReceipt = work.candidate.acceptedReceipt,
								isStillCurrent = ::validationIsCurrent,
								onValidated = onValidated
							)
						}
					} finally {
						workerStage.complete()
					}
				}
				workerJob.start()
			}
		} catch (failure: Throwable) {
			if (!completeImmediately(ReaderPageRelocationContentValidationResult.Invalidated)) {
				ownership.cancel()
				throw failure
			}
			return ReaderPageRelocationContentValidationHandle.Completed
		}
		ownership.attachCapture(captureHandle)
		return ownership
	}

	fun capturePreparedRasterPage(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		itemToken: String,
		previewGeneration: Long,
		priority: ReaderPageRasterPriority,
		mutationGeneration: ReaderForegroundWebViewMutationGeneration,
		isStillCurrent: () -> Boolean = { true },
		onStagingStarted: (ReaderPageSlideSnapshot, (Boolean) -> Unit) -> Unit,
		onCaptureFailed: () -> Unit,
		onCaptured: (ReaderPageRasterPublicationCompletion) -> Unit
	) {
		if (closed || frozenSnapshotCacheDomain != null) {
			onCaptureFailed()
			return
		}
		activeWebView = WeakReference(webView)
		val referenceLayout = readerPageRasterPhysicalLayout(reference)
		val physicalLayoutEpoch = referenceLayout?.let { layout ->
			admitPhysicalLayout(kind, layout)
		}
		val generation = activeGeneration
		if (
			!webView.isAttachedToWindow ||
			!isStillCurrent() ||
			physicalLayoutEpoch == null
		) {
			onCaptureFailed()
			return
		}
		cachedSnapshot(pageIndex, kind, reference)?.let { cached ->
			persistCachedSnapshot(
				cached,
				priority,
				mutationGeneration = mutationGeneration,
				isStillCurrent = isStillCurrent
			) { completion ->
				if (isStillCurrent()) onCaptured(completion)
			}
			return
		}
		capturePreparedPage(
			webView = webView,
			pageIndex = pageIndex,
			kind = kind,
			token = itemToken,
			previewGeneration = previewGeneration,
			mutationGeneration = mutationGeneration,
			generation = generation,
			isStillCurrent = isStillCurrent,
			onStagingStarted = { onPresented -> onStagingStarted(reference, onPresented) }
		) { captured ->
			if (
				captured == null ||
				!readerPageRasterPhysicalLayoutMatches(captured, reference) ||
				generation != activeGeneration ||
				physicalLayoutEpoch != rasterPhysicalLayoutEpoch.get() ||
				frozenSnapshotCacheDomain != null ||
				closed ||
				!isStillCurrent()
			) {
				captured?.releaseCacheOwnership()
				onCaptureFailed()
				return@capturePreparedPage
			}
			putSnapshot(
				snapshot = captured,
				priority = priority,
				mutationGeneration = mutationGeneration,
				isStillCurrent = isStillCurrent,
				onPersisted = onCaptured
			)
		}
	}

	private fun capturePreparedPage(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		token: String,
		previewGeneration: Long,
		mutationGeneration: ReaderForegroundWebViewMutationGeneration,
		generation: Long,
		isStillCurrent: () -> Boolean,
		onStagingStarted: ((Boolean) -> Unit) -> Unit,
		onCaptured: (ReaderPageSlideSnapshot?) -> Unit
	) {
		if (!isStillCurrent()) {
			onCaptured(null)
			return
		}
		val presentationTarget = runCatching {
			ReaderPageTurnPresentationTarget.Preview(
				token = token,
				pageIndex = pageIndex.toLong(),
				previewGeneration = previewGeneration,
				foregroundMutationGeneration = mutationGeneration.value
			)
		}.getOrNull()
		if (presentationTarget == null) {
			onCaptured(null)
			return
		}
		val captureStartedAt = SystemClock.uptimeMillis()
		val quotedToken = JSONObject.quote(token)
		onStagingStarted staging@{ presented ->
			if (
				!presented ||
				generation != activeGeneration ||
				!isStillCurrent()
			) {
				onCaptured(null)
				return@staging
			}
			webView.evaluateJavascript(
				"window.NavicReaderBridge?.exposePageTurnPreviewFinal?.(" +
					"$quotedToken, ${mutationGeneration.value}) === true"
			) { encoded ->
				if (
					generation != activeGeneration ||
					!isStillCurrent() ||
					!encoded.isJavascriptTrue()
				) {
					restoreLiveComposition(
						webView,
						token,
						mutationGeneration,
						isStillCurrent
					) { _ -> onCaptured(null) }
					return@evaluateJavascript
				}
				webView.postVisualStateCallback(
					visualStateRequestId.incrementAndGet(),
					object : WebView.VisualStateCallback() {
						override fun onComplete(requestId: Long) {
							if (
								generation != activeGeneration ||
								!webView.isAttachedToWindow ||
								!isStillCurrent()
							) {
								restoreLiveComposition(
									webView,
									token,
									mutationGeneration,
									isStillCurrent
								) { _ -> onCaptured(null) }
								return
							}
							webView.postOnAnimation frame@{
								if (!isStillCurrent()) {
									restoreLiveComposition(
										webView,
										token,
										mutationGeneration,
										isStillCurrent
									) { _ -> onCaptured(null) }
									return@frame
								}
								webView.evaluateJavascript(
									"window.NavicReaderBridge?." +
										"confirmPageTurnPreviewPresentation?.(" +
										"$quotedToken, ${mutationGeneration.value}) === true"
								) confirmation@{ confirmed ->
									if (
										generation != activeGeneration ||
										!isStillCurrent() ||
										!confirmed.isJavascriptTrue()
									) {
										restoreLiveComposition(
											webView,
											token,
											mutationGeneration,
											isStillCurrent
										) { _ -> onCaptured(null) }
										return@confirmation
									}
									capturePreparedSurface(
										webView = webView,
										target = presentationTarget,
										isStillCurrent = {
											generation == activeGeneration && isStillCurrent()
										}
									) { captured ->
										restoreLiveComposition(
											webView,
											token,
											mutationGeneration,
											isStillCurrent
										) { restored ->
											val bitmap = captured?.bitmap
											val snapshotGeometry = captured?.let {
												readerPagePreparedSnapshotGeometry(kind, it)
											}
											if (
												!restored ||
												captured == null ||
												bitmap == null ||
												snapshotGeometry == null ||
												generation != activeGeneration ||
												!isStillCurrent()
											) {
												bitmap?.takeUnless { it.isRecycled }?.recycle()
												onCaptured(null)
												return@restoreLiveComposition
											}
											onCaptured(
												ReaderPageSlideSnapshot(
													key = snapshotKey(
														pageIndex,
														kind,
														bitmap,
														snapshotGeometry.surfaceRectInWindow
													),
													bitmap = bitmap,
													surfaceRectInWindow = snapshotGeometry.surfaceRectInWindow,
													leafGeometry = snapshotGeometry.leafGeometry,
													reverseFaceColor = snapshotGeometry.reverseFaceColor,
													captureMillis = SystemClock.uptimeMillis() - captureStartedAt
												)
											)
										}
									}
								}
							}
						}
					}
				)
			}
		}
	}

	fun cacheCurrentSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		current: ReaderPageTurnCaptureResult,
		generation: Long = activeGeneration,
		persist: Boolean = true
	): ReaderPageSlideSnapshot? {
		if (closed || frozenSnapshotCacheDomain != null) {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return null
		}
		return cacheSnapshot(pageIndex, kind, current, generation, persist)
	}

	fun ensurePersistentSnapshot(
		snapshot: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		mutationGeneration: ReaderForegroundWebViewMutationGeneration? = null,
		isStillCurrent: () -> Boolean = { true },
		onPersisted: (ReaderPageRasterPublicationCompletion) -> Unit
	) {
		persistCachedSnapshot(
			snapshot = snapshot,
			priority = priority,
			mutationGeneration = mutationGeneration,
			isStillCurrent = isStillCurrent,
			onPersisted = onPersisted
		)
	}

	private fun cacheSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		current: ReaderPageTurnCaptureResult,
		generation: Long,
		persist: Boolean = true
	): ReaderPageSlideSnapshot? {
		if (generation != activeGeneration) {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return null
		}
		val key = snapshotKey(pageIndex, kind, current.bitmap, current.sourceRectInWindow)
		val leafGeometry = current.geometry.leafGeometry(current.bitmap.width, current.bitmap.height) ?: run {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return null
		}
		if (!readerPageRasterGeometryMatches(kind, leafGeometry)) {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return null
		}
		val snapshot = ReaderPageSlideSnapshot(
			key = key,
			bitmap = current.bitmap,
			surfaceRectInWindow = Rect(current.sourceRectInWindow),
			leafGeometry = leafGeometry,
			reverseFaceColor = readerPageTurnOpaqueColor(current.geometry.reverseFaceColorArgb),
			captureMillis = current.elapsedMs
		)
		val physicalLayout = readerPageRasterPhysicalLayout(snapshot) ?: run {
			snapshot.releaseCacheOwnership()
			return null
		}
		activatePhysicalLayout(kind, physicalLayout)
		return putSnapshot(
			snapshot = snapshot,
			priority = ReaderPageRasterPriority.Current,
			persist = persist
		)
	}

	private fun cachedSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot? = null
	): ReaderPageSlideSnapshot? {
		if (closed || frozenSnapshotCacheDomain != null) return null
		val referenceLayout = reference?.let(::readerPageRasterPhysicalLayout)
		if (reference != null && referenceLayout == null) return null
		if (referenceLayout != null && admitPhysicalLayout(kind, referenceLayout) == null) return null
		return snapshotCache.entries
			.lastOrNull { (key, snapshot) ->
				key.visualPageIndex == pageIndex &&
					key.kind == kind &&
					(reference == null || readerPageRasterPhysicalLayoutMatches(snapshot, reference))
			}
			?.let { (key, value) ->
				snapshotCache[key]
				value
			}
	}

	private fun admitPhysicalLayout(
		kind: ReaderPageTurnTransitionKind,
		layout: ReaderPageRasterPhysicalLayout
	): Long? {
		if (closed || frozenSnapshotCacheDomain != null) return null
		physicalLayoutAuthority?.let { authority ->
			return authority.epoch.takeIf {
				authority.kind == kind && layout.matches(authority.layout)
			}
		}
		return activatePhysicalLayout(kind, layout)
	}

	private fun activatePhysicalLayout(
		kind: ReaderPageTurnTransitionKind,
		layout: ReaderPageRasterPhysicalLayout
	): Long {
		physicalLayoutAuthority?.let { authority ->
			if (authority.kind == kind && layout.matches(authority.layout)) return authority.epoch
		}
		val epoch = rasterPhysicalLayoutEpoch.incrementAndGet()
		physicalLayoutAuthority = ReaderPageRasterPhysicalLayoutAuthority(kind, layout, epoch)
		publicationLedger.invalidate()
		publicationScheduler.cancelBeforeEpoch(publicationLedger.currentEpoch())
		removeIncompatibleSnapshots(kind, layout)
		return epoch
	}

	private fun currentPhysicalLayoutEpoch(
		kind: ReaderPageTurnTransitionKind,
		layout: ReaderPageRasterPhysicalLayout
	): Long? = physicalLayoutAuthority?.let { authority ->
		authority.epoch.takeIf {
			authority.kind == kind && layout.matches(authority.layout)
		}
	}

	private fun removeIncompatibleSnapshots(
		kind: ReaderPageTurnTransitionKind,
		layout: ReaderPageRasterPhysicalLayout
	) {
		val incompatible = snapshotCache.entries
			.filter { (key, snapshot) ->
				key.kind == kind &&
					readerPageRasterPhysicalLayout(snapshot)?.matches(layout) != true
			}
			.map { entry -> entry.key to entry.value }
		incompatible.forEach { (key, snapshot) ->
			removeCachedSnapshot(key, snapshot)?.releaseCacheOwnership()
		}
	}

	private fun markCachedSnapshotDurable(snapshot: ReaderPageSlideSnapshot) {
		if (
			snapshotCache[snapshot.key] === snapshot &&
			(
				protectedEncodedPageIndices.isEmpty() ||
					snapshot.key.visualPageIndex in protectedEncodedPageIndices
			)
		) {
			snapshotDurability[snapshot] =
				ReaderPageRasterHydrationDurability.PersistentStoreVerified
		}
	}

	private fun persistCachedSnapshot(
		snapshot: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		mutationGeneration: ReaderForegroundWebViewMutationGeneration? = null,
		isStillCurrent: () -> Boolean = { true },
		onPersisted: (ReaderPageRasterPublicationCompletion) -> Unit
	) {
		schedulePersistentSnapshot(
			snapshot = snapshot,
			priority = priority,
			mutationGeneration = mutationGeneration,
			isStillCurrent = isStillCurrent
		) { completion ->
			if (!runCatching(isStillCurrent).getOrDefault(false)) return@schedulePersistentSnapshot
			if (completion.result == ReaderPageRasterPublicationResult.Durable) {
				markCachedSnapshotDurable(snapshot)
			}
			onPersisted(completion)
		}
	}

	private fun retainSnapshotForRecipients(
		snapshot: ReaderPageSlideSnapshot,
		count: Int
	) {
		require(count >= 0)
		var retained = 0
		try {
			repeat(count) {
				snapshot.retain()
				retained += 1
			}
		} catch (failure: Throwable) {
			repeat(retained) { snapshot.release() }
			throw failure
		}
	}

	private fun putSnapshot(
		snapshot: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		persist: Boolean = true,
		exactRasterIdentity: String? = null,
		recipientRetainCount: Int = 0,
		mutationGeneration: ReaderForegroundWebViewMutationGeneration? = null,
		isStillCurrent: () -> Boolean = { true },
		onPersisted: (ReaderPageRasterPublicationCompletion) -> Unit = {}
	): ReaderPageSlideSnapshot {
		check(!closed && frozenSnapshotCacheDomain == null) {
			"Cannot admit snapshot cache ownership after lifecycle admission closes"
		}
		val physicalLayout = checkNotNull(readerPageRasterPhysicalLayout(snapshot))
		check(currentPhysicalLayoutEpoch(snapshot.key.kind, physicalLayout) != null) {
			"Cannot cache a page snapshot outside the active physical layout"
		}
		snapshotCache[snapshot.key]?.let { cached ->
			val mayReuse = cached === snapshot ||
				exactRasterIdentity == null ||
				snapshotExactRasterIdentities[cached] == exactRasterIdentity
			if (mayReuse) {
				retainSnapshotForRecipients(cached, recipientRetainCount)
				try {
					if (cached === snapshot) {
						snapshotCacheTokens.putIfAbsent(
							cached,
							snapshotCacheOwnershipTokenAllocator.allocate()
						)
						exactRasterIdentity?.let { identity ->
							snapshotExactRasterIdentities[cached] = identity
						}
					} else {
						snapshot.releaseCacheOwnership()
					}
					if (persist) {
						persistCachedSnapshot(
							snapshot = cached,
							priority = priority,
							mutationGeneration = mutationGeneration,
							isStillCurrent = isStillCurrent,
							onPersisted = onPersisted
						)
					}
				} catch (failure: Throwable) {
					repeat(recipientRetainCount) { cached.release() }
					throw failure
				}
				return cached
			}
			removeCachedSnapshot(snapshot.key, cached)?.releaseCacheOwnership()
		}
		retainSnapshotForRecipients(snapshot, recipientRetainCount)
		try {
			snapshotCache[snapshot.key] = snapshot
			snapshotCacheTokens[snapshot] = snapshotCacheOwnershipTokenAllocator.allocate()
			exactRasterIdentity?.let { identity ->
				snapshotExactRasterIdentities[snapshot] = identity
			}
			snapshotDurability[snapshot] =
				ReaderPageRasterHydrationDurability.RequiresPublication
			if (persist) {
				persistCachedSnapshot(
					snapshot = snapshot,
					priority = priority,
					mutationGeneration = mutationGeneration,
					isStillCurrent = isStillCurrent,
					onPersisted = onPersisted
				)
			}
			trimSnapshotCacheToCapacity()
			Logger.i(
				ReaderPageTurnBundleSourceTag,
				"Page-turn snapshot cached key=${snapshot.key} entries=${snapshotCache.keys}"
			)
		} catch (failure: Throwable) {
			removeCachedSnapshot(snapshot.key, snapshot)?.releaseCacheOwnership()
			repeat(recipientRetainCount) { snapshot.release() }
			throw failure
		}
		return snapshot
	}

	private fun trimSnapshotCacheToCapacity() {
		while (snapshotCache.size > MaxCachedSnapshots) {
			val eviction = snapshotCache.entries.firstOrNull { (key, _) ->
				key.visualPageIndex !in protectedSnapshotPageIndices
			} ?: break
			removeCachedSnapshot(eviction.key, eviction.value)?.releaseCacheOwnership()
		}
	}

	private fun schedulePersistentSnapshot(
		snapshot: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		mutationGeneration: ReaderForegroundWebViewMutationGeneration? = null,
		expectedDescriptor: ReaderPageRasterDescriptor? = null,
		publicationDescriptor: ReaderPageRasterDescriptor? = null,
		isStillCurrent: () -> Boolean = { true },
		onPersisted: (ReaderPageRasterPublicationCompletion) -> Unit = {}
	) {
		val failedCompletion = ReaderPageRasterPublicationCompletion(
			ReaderPageRasterPublicationResult.Failed
		)
		if (!runCatching(isStillCurrent).getOrDefault(false)) {
			onPersisted(failedCompletion)
			return
		}
		val pageIndex = snapshot.key.visualPageIndex
		if (closed) {
			rasterPersistenceSkipped(
				pageIndex,
				"bundle-source-closed",
				activeGeneration
			)
			onPersisted(failedCompletion)
			return
		}
		val webView = activeWebView.get()?.takeIf { it.isAttachedToWindow }
			?: run {
				rasterPersistenceSkipped(
					pageIndex,
					"webview-unavailable",
					activeGeneration
				)
				onPersisted(failedCompletion)
				return
			}
		val generation = activeGeneration
		val physicalLayout = readerPageRasterPhysicalLayout(snapshot)
		val physicalLayoutEpoch = physicalLayout?.let { layout ->
			currentPhysicalLayoutEpoch(snapshot.key.kind, layout)
		}
		if (physicalLayout == null || physicalLayoutEpoch == null) {
			rasterPersistenceSkipped(pageIndex, "physical-layout-stale", generation)
			onPersisted(failedCompletion)
			return
		}
		val descriptorOwner = pendingDescriptorOwners.acquire(snapshot) {
			onPersisted(failedCompletion)
		} ?: run {
			rasterPersistenceSkipped(
				pageIndex,
				"bundle-source-closed",
				generation
			)
			onPersisted(failedCompletion)
			return
		}
		val descriptorMethod = if (expectedDescriptor == null) {
			"pageTurnRasterDescriptor"
		} else {
			"pageTurnPassiveRasterDescriptor"
		}
		try {
			webView.evaluateJavascript(
				"JSON.stringify(window.NavicReaderBridge?.$descriptorMethod?.($pageIndex) ?? null)"
			) callback@{ encodedDescriptor ->
				val claimedOwner = pendingDescriptorOwners.claim(descriptorOwner)
					?: return@callback
				try {
				if (
					closed ||
					!runCatching(isStillCurrent).getOrDefault(false) ||
					generation != activeGeneration ||
					physicalLayoutEpoch != rasterPhysicalLayoutEpoch.get()
				) {
					rasterPersistenceSkipped(
						pageIndex,
						"generation-or-physical-layout-changed",
						generation
					)
					onPersisted(failedCompletion)
					return@callback
				}
				val descriptor = readerPageRasterDescriptor(encodedDescriptor)
					?: run {
						rasterPersistenceSkipped(
							pageIndex,
							"descriptor-unavailable",
							generation
						)
						onPersisted(failedCompletion)
						return@callback
					}
				if (expectedDescriptor != null && descriptor != expectedDescriptor) {
					rasterPersistenceSkipped(pageIndex, "descriptor-mismatch", generation)
					onPersisted(failedCompletion)
					return@callback
				}
				val persistentDescriptor = publicationDescriptor ?: descriptor
				cacheRasterDescriptor(
					ReaderPageRasterDescriptorIdentity(
						generation = generation,
						quality = snapshot.key.bitmapQuality,
						pageIndex = pageIndex,
						kind = snapshot.key.kind,
						physicalLayout = physicalLayout,
						physicalLayoutEpoch = physicalLayoutEpoch
					),
					persistentDescriptor
				)
				val key = persistentDescriptor.key(snapshot.key.bitmapQuality)
				val request = ReaderRasterPersistenceRequest(
					ReaderRasterPersistenceRestartContract(
						snapshot = WeakReference(snapshot),
						webView = WeakReference(webView),
						key = key,
						metadata = snapshot.toRasterMetadata(),
						captureMillis = snapshot.captureMillis.coerceAtLeast(0L),
						priority = priority,
						generation = generation,
						physicalLayoutEpoch = physicalLayoutEpoch,
						mutationGeneration = mutationGeneration,
						isStillCurrent = isStillCurrent,
						onPersisted = onPersisted
					)
				)
				val admitted = synchronized(rasterInitializationFenceLock) {
					// A claimed pre-fence descriptor carries logical demand, not new physical work.
					if (closed ||
						rasterPersistenceRequests.size >= ReaderPageMaximumPublicationCallbacks
					) false else rasterPersistenceRequests.add(request)
				}
				if (admitted) launchRasterPersistenceRequest(request)
				else onPersisted(failedCompletion)
			} finally {
				pendingDescriptorOwners.complete(claimedOwner)
			}
		}
		} catch (failure: Throwable) {
			val claimedOwner = pendingDescriptorOwners.claim(descriptorOwner)
				?: return
			try {
				pendingDescriptorOwners.abandon(claimedOwner)
			} catch (cleanupFailure: Throwable) {
				if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
			}
			try {
				rasterPersistenceSkipped(
					pageIndex,
					"descriptor-request-failed",
					generation
				)
			} catch (reportingFailure: Throwable) {
				if (reportingFailure !== failure) failure.addSuppressed(reportingFailure)
			}
			publicationLedger.recordFailure(failure)
		}
	}

	private fun completeRasterPersistenceRequest(
		request: ReaderRasterPersistenceRequest,
		completion: ReaderPageRasterPublicationCompletion
	) {
		val deliver = synchronized(rasterInitializationFenceLock) {
			when {
				request.completed -> false
				!closed && (frozenSnapshotCacheDomain != null || rasterGenerationAndPersistenceOwnership.isFrozen) -> {
					request.restartRequired = true
					false
				}
				else -> {
					request.completed = true
					request.restartRequired = false
					rasterPersistenceRequests.remove(request)
					true
				}
			}
		}
		if (deliver) {
			try {
				request.contract.onPersisted(completion)
			} catch (failure: Throwable) {
				publicationLedger.recordFailure(failure)
			}
		}
	}

	private fun launchRasterPersistenceRequest(request: ReaderRasterPersistenceRequest) {
		val control = ReaderRasterGenerationPersistenceJobControl()
		val owner = synchronized(rasterInitializationFenceLock) {
			if (request.completed || request.attemptActive || closed) return
			if (frozenSnapshotCacheDomain != null || rasterGenerationAndPersistenceOwnership.isFrozen) {
				request.restartRequired = true
				return
			}
			rasterGenerationAndPersistenceOwnership.admit(
				ReaderExactPhysicalOwnerDescriptor(
					kind = ReaderTransitionResourceKind.Raster,
					origin = ReaderLegacyResourceOrigin.Pending,
					state = ReaderLegacyResourceState.Reserved
				),
				control::cancel
			)?.also {
				request.attemptActive = true
				request.restartRequired = false
				rasterPersistenceAttempts.add(control)
			}
		} ?: return
		val failedCompletion = ReaderPageRasterPublicationCompletion(ReaderPageRasterPublicationResult.Failed)
		var job: Job? = null
		fun settle() {
			control.settle { bitmap, transferred ->
				try {
					bitmap?.let(ReaderAndroidPageRasterCodec::release)
					if (!transferred) completeRasterPersistenceRequest(request, failedCompletion)
				} finally {
					synchronized(rasterInitializationFenceLock) {
						request.attemptActive = false
						rasterPersistenceAttempts.remove(control)
					}
					synchronized(rasterPersistenceJobLock) { job?.let(rasterPersistenceJobs::remove) }
					rasterGenerationAndPersistenceOwnership.complete(owner)
				}
			}
		}
		try {
			val contract = request.contract
			val snapshot = contract.snapshot.get()
			if (snapshot == null || snapshot.bitmap.isRecycled ||
				contract.generation != activeGeneration ||
				contract.physicalLayoutEpoch != rasterPhysicalLayoutEpoch.get() ||
				!runCatching(contract.isStillCurrent).getOrDefault(false)
			) {
				settle()
				return
			}
			if (!synchronized(rasterInitializationFenceLock) { rasterInitializationIsOpen() }) {
				settle()
				return
			}
			val bitmap = snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, false)
				?: throw IllegalStateException("Raster persistence bitmap copy failed")
			control.own(bitmap)
			val value = ReaderPageRasterGeneration(contract.metadata, bitmap, contract.captureMillis)
			val launched = rasterScope.launch(start = CoroutineStart.LAZY) {
				val entered = synchronized(rasterInitializationFenceLock) {
					rasterInitializationIsOpen() && rasterGenerationAndPersistenceOwnership
						.updateState(owner, ReaderLegacyResourceState.Running)
				}
				if (!entered) return@launch
				runRasterPersistenceRequest(request, value, control)
			}
			job = launched
			trackRasterPersistenceJob(launched) { settle() }
			control.attach(launched)
			onOwnershipMutated()
			if (synchronized(rasterInitializationFenceLock) { rasterInitializationIsOpen() }) {
				launched.start()
			} else {
				launched.cancel()
			}
		} catch (failure: Throwable) {
			publicationLedger.recordFailure(failure)
			val failedJob = job
			if (failedJob == null) settle()
			else {
				failedJob.cancel()
				if (failedJob.isCompleted) settle()
			}
		}
	}

	private suspend fun runRasterPersistenceRequest(
		request: ReaderRasterPersistenceRequest,
		rasterGeneration: ReaderPageRasterGeneration<Bitmap>,
		control: ReaderRasterGenerationPersistenceJobControl
	) {
		val contract = request.contract
		val webView = contract.webView.get() ?: return
		val key = contract.key
		val pageIndex = key.visualPageOrdinal
		val generation = contract.generation
		val physicalLayoutEpoch = contract.physicalLayoutEpoch
		val mutationGeneration = contract.mutationGeneration
		val isStillCurrent = contract.isStillCurrent
		val failedCompletion = ReaderPageRasterPublicationCompletion(ReaderPageRasterPublicationResult.Failed)
		var publicationValueTransferred = false
		try {
			val scheduler = rasterScheduler(webView)
			if (
				closed ||
				!runCatching(isStillCurrent).getOrDefault(false) ||
				generation != activeGeneration ||
				physicalLayoutEpoch != rasterPhysicalLayoutEpoch.get()
			) {
				rasterPersistenceSkipped(
					pageIndex,
					"generation-or-physical-layout-changed",
					generation
				)
				completeRasterPersistenceRequest(request, failedCompletion)
				return
			}
			scheduler.activateProfile(key.profile)
			stageEncodedWindowProtection(key.profile)
			val protectedCenter = protectedEncodedCenterPageIndex
			if (protectedCenter != null && protectedEncodedPageIndices.isNotEmpty()) {
				scheduler.protectEncodedWindow(
					profile = key.profile,
					centerPageOrdinal = protectedCenter,
					pinnedPageOrdinals = protectedEncodedPageIndices
				)
			}
			val value = ReaderPageRasterPublicationValue(
				key = key,
				generation = rasterGeneration
			)
			val publicationEpoch = publicationLedger.currentEpoch()
			val publicationRequest = ReaderPageRasterPublicationRequest(
				digest = key.digest,
				epoch = publicationEpoch,
				mutationGeneration = mutationGeneration
			)
			val publicationStartedAt = diagnostics?.now() ?: 0L
			val persistenceAttemptId = ReaderPagePersistenceAttemptId(
				persistenceAttemptIds.incrementAndGet()
			)
			var publicationQaFaultCorrelation:
				ReaderPageQaFaultCorrelation? = null
			val registration = publicationLedger.begin(
				digest = key.digest,
				value = value,
				mutationGeneration = mutationGeneration
			) { persisted ->
				val publicationCompletion = when {
					persisted -> ReaderPageRasterPublicationCompletion(
						ReaderPageRasterPublicationResult.Durable
					)
					closed || publicationEpoch != publicationLedger.currentEpoch() ->
						failedCompletion
					else -> publicationCompletionResults[publicationRequest]
						?: failedCompletion
				}
				val publicationResult = publicationCompletion.result
				diagnostics?.publication(
					digest = key.digest,
					rasterEpoch = publicationEpoch,
					persistenceAttemptId = persistenceAttemptId,
					result = when {
						persisted -> ReaderPagePublicationDiagnosticResult.Durable
						closed -> ReaderPagePublicationDiagnosticResult.Cancelled
						publicationEpoch != publicationLedger.currentEpoch() ->
							ReaderPagePublicationDiagnosticResult.Stale
						publicationResult ==
							ReaderPageRasterPublicationResult.CapacityReached ->
							ReaderPagePublicationDiagnosticResult.CapacityReached
						else -> ReaderPagePublicationDiagnosticResult.Failed
					},
					startedAtMs = publicationStartedAt,
					qaFaultCorrelation = publicationQaFaultCorrelation
				)
				publicationQaFaultCorrelation
					?.takeIf { correlation ->
						correlation.relation == ReaderPageQaFaultRelation.Retry &&
							persistenceRetryCorrelations[key.digest]
								?.requestId == correlation.requestId
					}
					?.let { persistenceRetryCorrelations.remove(key.digest) }
				if (publicationResult == ReaderPageRasterPublicationResult.Failed) {
					rasterPersistenceSkipped(
						pageIndex,
						"durable-publication-failed",
						generation
					)
				}
				completeRasterPersistenceRequest(request, publicationCompletion)
			}
			publicationQaFaultCorrelation =
				readerPageRasterPublicationRetryCorrelation(
					registration,
					persistenceRetryCorrelations[key.digest]
				)
			publicationValueTransferred = true
			control.transfer()
			when (registration) {
				is ReaderPageRasterPublicationRegistration.Started -> {
					scheduleRasterPublication(
						request = registration.request,
						physicalLayoutEpoch = physicalLayoutEpoch,
						persistenceAttemptId = persistenceAttemptId,
						isStillCurrent = isStillCurrent,
						onQaFaultApplied = { correlation ->
							publicationQaFaultCorrelation = correlation
						}
					)
				}
				is ReaderPageRasterPublicationRegistration.Coalesced ->
					Unit
				is ReaderPageRasterPublicationRegistration.Rejected ->
					rasterPersistenceSkipped(
						pageIndex,
						"publication-${
							registration.reason.name.lowercase()
						}",
						generation
					)
			}
		} catch (failure: CancellationException) {
			if (!publicationValueTransferred) {
				completeRasterPersistenceRequest(request, failedCompletion)
			}
			throw failure
		} catch (failure: Throwable) {
			publicationLedger.recordFailure(failure)
			rasterPersistenceSkipped(
				pageIndex,
				"publication-initialization-failed",
				generation
			)
			if (!publicationValueTransferred) {
				completeRasterPersistenceRequest(request, failedCompletion)
			}
		}
	}

	private fun scheduleRasterPublication(
		request: ReaderPageRasterPublicationRequest,
		physicalLayoutEpoch: Long,
		persistenceAttemptId: ReaderPagePersistenceAttemptId,
		isStillCurrent: () -> Boolean,
		onQaFaultApplied: (ReaderPageQaFaultCorrelation) -> Unit
	) {
		publicationScheduler.schedule(request) {
			fun publicationIsCurrent(): Boolean =
				physicalLayoutEpoch == rasterPhysicalLayoutEpoch.get() &&
					runCatching(isStillCurrent).getOrDefault(false)

			val currentBeforeAcquisition = publicationIsCurrent()
			val value = publicationLedger.acquireForPersistence(request)
				?: return@schedule
			var write = ReaderPageRasterWriteResult(
				persisted = false,
				ownership = ReaderPageRasterValueOwnership.Caller
			)
			val store = persistentStore
			var writeFailure: Throwable? = null
			var publicationQaFault: ReaderPageQaAppliedFault? = null
			try {
				if (!currentBeforeAcquisition) return@schedule
				publicationQaFault = qaFaultRegistry?.consumeAndApply(
					ReaderPageQaFault.FailNextPersistence,
					ReaderPageQaFaultOperationContext(
						publicationEpoch = request.epoch,
						persistenceAttemptId = persistenceAttemptId.value
					)
				)
				publicationQaFault?.correlation()?.let { correlation ->
					persistenceRetryCorrelations[value.key.digest] = correlation
					onQaFaultApplied(correlation)
				}
				try {
					withContext(NonCancellable + Dispatchers.IO) {
						try {
							val metadata = value.generation.metadata
							val commitFence = ReaderPageRasterCommitFence { action ->
								if (publicationIsCurrent()) {
									publicationLedger.commitFence(request).commit {
										if (publicationIsCurrent()) {
											action()
										} else {
											ReaderPageRasterWriteResult(
												persisted = false,
												ownership = ReaderPageRasterValueOwnership.Caller
											)
										}
									}
								} else {
									ReaderPageRasterWriteResult(
										persisted = false,
										ownership = ReaderPageRasterValueOwnership.Caller
									)
								}
							}
							write = when {
								!publicationIsCurrent() -> write
								store?.contains(value.key, metadata) == true ->
									ReaderPageRasterWriteResult(
										persisted = true,
										ownership = ReaderPageRasterValueOwnership.Caller
									)
								else -> store?.writePublication(
									key = value.key,
									metadata = metadata,
									value = value.generation.value,
									commitFence = commitFence
								) ?: write
							}
							if (publicationQaFault != null && write.persisted) {
								write.receipt?.let { receipt ->
									store?.rollbackPublication(receipt)
								}
								write = write.copy(persisted = false, receipt = null)
							}
						} catch (failure: Throwable) {
							writeFailure = failure
							if (publicationQaFault != null) {
								write = write.copy(persisted = false, receipt = null)
							}
						}
					}
				} catch (_: CancellationException) {
					// The non-cancellable worker already captured its write result.
				} catch (failure: Throwable) {
					writeFailure = failure
				}
				writeFailure?.let(publicationLedger::recordFailure)
				check(
					write.ownership == ReaderPageRasterValueOwnership.Caller
				) {
					"Publication store adopted a ledger-owned value"
				}
				qaFaultRegistry?.pausePublicationWithinWorker(request.epoch)
					?.let { applied ->
						publicationQaFault = applied
						onQaFaultApplied(applied.correlation())
					}
			} finally {
				val publicationCurrent = publicationIsCurrent()
				val persistedForCurrentPublication = write.persisted && publicationCurrent
				val publicationCompletion = readerPageRasterPublicationCompletion(
					persistedForCurrentPublication = persistedForCurrentPublication,
					publicationCurrent = publicationCurrent,
					writeFailureReason = write.failureReason
				)
				publicationCompletionResults[request] = publicationCompletion
				val accepted = try {
					publicationLedger.complete(
						request = request,
						persisted = persistedForCurrentPublication
					)
				} finally {
					publicationCompletionResults.remove(request)
				}
				if (!accepted || !persistedForCurrentPublication) {
					write.receipt?.let { receipt ->
						var rollbackFailure: Throwable? = null
						try {
							withContext(NonCancellable + Dispatchers.IO) {
								try {
									store?.rollbackPublication(receipt)
								} catch (failure: Throwable) {
									rollbackFailure = failure
								}
							}
						} catch (_: CancellationException) {
							// Rollback already completed on the non-cancellable worker.
						} catch (failure: Throwable) {
							rollbackFailure = failure
						}
						rollbackFailure?.let(publicationLedger::recordFailure)
					}
				}
			}
		}
	}

	private fun rasterPersistenceSkipped(
		pageIndex: Int,
		reason: String,
		requestGeneration: Long
	) {
		val diagnosticKey = "$pageIndex:$reason"
		if (!rasterPersistenceDiagnostics.add(diagnosticKey)) return
		while (rasterPersistenceDiagnostics.size > 64) {
			rasterPersistenceDiagnostics.remove(rasterPersistenceDiagnostics.first())
		}
		Logger.w(
			ReaderPageTurnBundleSourceTag,
			"Page raster persistence skipped page=$pageIndex reason=$reason " +
				"requestGeneration=$requestGeneration activeGeneration=$activeGeneration"
		)
	}

	private fun trackRasterPersistenceJob(
		job: Job,
		onSettled: () -> Unit
	) {
		synchronized(rasterPersistenceJobLock) {
			rasterPersistenceJobs += job
		}
		job.invokeOnCompletion {
			try {
				onSettled()
			} finally {
				synchronized(rasterPersistenceJobLock) {
					rasterPersistenceJobs -= job
				}
			}
		}
	}

	private suspend fun rasterScheduler(
		webView: WebView,
		restartInitialization: WeakReference<WebView>? = null
	): ReaderPageRasterScheduler<Bitmap> {
		val initializationJob = checkNotNull(currentCoroutineContext()[Job]) {
			"Raster persistence initialization requires a coroutine job"
		}
		val initializationOwner = rasterGenerationAndPersistenceOwnership.admit(
			ReaderExactPhysicalOwnerDescriptor(
				kind = ReaderTransitionResourceKind.Raster,
				origin = ReaderLegacyResourceOrigin.Pending,
				state = ReaderLegacyResourceState.Reserved
			),
			cancelPhysical = {
				initializationJob.cancel()
				true
			}
		) ?: throw CancellationException("Raster persistence initialization is frozen")
		return try {
			rasterInitializationMutex.withLock {
				requireRasterInitializationOpen()
				rasterScheduler?.let { return@withLock it }
				var cache: ReaderPageRasterCache<Bitmap>? = null
				var store: ReaderPageRasterCacheStore<Bitmap>? = null
				var scheduler: ReaderPageRasterScheduler<Bitmap>? = null
				try {
					requireRasterInitializationOpen()
					withContext(rasterInitializationDispatcher) {
						synchronized(rasterInitializationFenceLock) {
							requireRasterInitializationOpen()
							check(rasterGenerationAndPersistenceOwnership.updateState(
								initializationOwner, ReaderLegacyResourceState.Running
							)) { "Raster persistence initialization lost physical ownership" }
						}
						ReaderPageRasterCache(
							root = readerPageRasterStorageRoot(webView.context.applicationContext),
							codec = ReaderAndroidPageRasterCodec,
							onDiagnostic = { diagnostic ->
								Logger.w(ReaderPageTurnBundleSourceTag, "Page raster cache $diagnostic")
							},
							onOwnershipMutated = onOwnershipMutated,
							ownershipTokenAllocator = storeAndCacheOwnershipTokenAllocator
						).also { created -> cache = created }
					}
					requireRasterInitializationOpen()
					val createdCache = checkNotNull(cache)
					val createdStore = ReaderPageRasterCacheStore(
						createdCache,
						storeAndCacheOwnershipTokenAllocator
					)
					store = createdStore
					val createdScheduler = ReaderPageRasterScheduler(
						scope = rasterScope,
						store = createdStore,
						generator = ReaderPageRasterGenerator { null },
						release = ReaderAndroidPageRasterCodec::release,
						tokenAllocator = rasterGenerationAndPersistenceOwnershipTokenAllocator
					)
					scheduler = createdScheduler
					requireRasterInitializationOpen()
					createdCache.protectDecodedPageIndices(protectedSnapshotPageIndices)
					val protectedCenter = protectedEncodedCenterPageIndex
					val protectedProfile = protectedEncodedProfile
					if (
						protectedCenter != null &&
						protectedProfile != null &&
						protectedEncodedPageIndices.isNotEmpty()
					) {
						createdCache.stageEncodedWindowProtection(
							profile = protectedProfile,
							centerPageOrdinal = protectedCenter,
							pinnedPageOrdinals = protectedEncodedPageIndices
						)
					}
					val published = synchronized(rasterInitializationFenceLock) {
						requireRasterInitializationOpen()
						rasterCache = createdCache
						persistentStore = createdStore
						rasterScheduler = createdScheduler
						createdScheduler
					}
					onOwnershipMutated()
					published
				} catch (failure: Throwable) {
					closeUnpublishedRasterOwners(
						cache = cache,
						store = store,
						scheduler = scheduler,
						failure = failure
					)
					throw failure
				}
			}
		} finally {
			synchronized(rasterInitializationFenceLock) {
				if (!closed && restartInitialization != null && rasterGenerationAndPersistenceOwnership.isFrozen) {
					rasterInitializationRestarts.add(restartInitialization)
				}
			}
			rasterGenerationAndPersistenceOwnership.complete(initializationOwner)
		}
	}

	private fun rasterInitializationIsOpen(): Boolean {
		val fenced = synchronized(closeFenceLock) { closed }
		return !fenced && frozenSnapshotCacheDomain == null &&
			!rasterGenerationAndPersistenceOwnership.isFrozen && rasterJob.isActive
	}

	private fun requireRasterInitializationOpen() {
		if (!rasterInitializationIsOpen()) {
			throw CancellationException(
				"Raster persistence initialization is closed"
			)
		}
	}

	private suspend fun closeUnpublishedRasterOwners(
		cache: ReaderPageRasterCache<Bitmap>?,
		store: ReaderPageRasterCacheStore<Bitmap>?,
		scheduler: ReaderPageRasterScheduler<Bitmap>?,
		failure: Throwable
	) {
		withContext(NonCancellable) {
			try {
				scheduler?.closeAndJoin()
			} catch (cleanupFailure: Throwable) {
				if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
			}
			try {
				store?.close()
			} catch (cleanupFailure: Throwable) {
				if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
			}
			try {
				cache?.close()
			} catch (cleanupFailure: Throwable) {
				if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
			}
		}
	}

	private fun ReaderPageSlideSnapshot.toRasterMetadata(): ReaderPageRasterMetadata = ReaderPageRasterMetadata(
		surfaceLeft = 0,
		surfaceTop = 0,
		surfaceRight = bitmap.width,
		surfaceBottom = bitmap.height,
		fullLeafRect = leafGeometry.fullLeafRect?.toRasterRect(),
		leftLeafRect = leafGeometry.leftLeafRect?.toRasterRect(),
		gutterRect = leafGeometry.gutterRect?.toRasterRect(),
		rightLeafRect = leafGeometry.rightLeafRect?.toRasterRect(),
		reverseFaceColor = reverseFaceColor
	)

	private fun paige.navic.reader.ReaderPageTurnPixelRect.toRasterRect() = ReaderPageRasterRect(
		left = left,
		top = top,
		right = right,
		bottom = bottom
	)

	private fun snapshotKey(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		bitmap: Bitmap,
		surfaceRectInWindow: Rect
	): ReaderPageSlideSnapshotKey = ReaderPageSlideSnapshotKey(
		visualPageIndex = pageIndex,
		kind = kind,
		bitmapQuality = bitmapQuality,
		bitmapWidth = bitmap.width,
		bitmapHeight = bitmap.height,
		surfaceWidth = surfaceRectInWindow.width(),
		surfaceHeight = surfaceRectInWindow.height()
	)

	internal fun captureStagedSurface(
		webView: WebView,
		geometry: ReaderPageTurnCaptureGeometry,
		sourceRectInWindow: Rect,
		onCaptured: (Bitmap?) -> Unit
	) {
		if (geometry.pages.isEmpty()) {
			onCaptured(null)
			return
		}
		captureCompositedSurface(
			webView = webView,
			sourceRectInWindow = sourceRectInWindow,
			backgroundColor = readerPageTurnOpaqueColor(geometry.reverseFaceColorArgb),
			onCaptured = onCaptured
		)
	}

	private fun capturePreparedSurface(
		webView: WebView,
		target: ReaderPageTurnPresentationTarget.Preview,
		isStillCurrent: () -> Boolean,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) {
		bitmapSource.capturePresentedSurface(
			webView = webView,
			target = target,
			isStillCurrent = isStillCurrent,
			onCaptured = onCaptured
		)
	}

	private fun captureCompositedSurface(
		webView: WebView,
		sourceRectInWindow: Rect,
		backgroundColor: Int,
		onCaptured: (Bitmap?) -> Unit
	) {
		if (!webView.isAttachedToWindow || sourceRectInWindow.width() <= 0 || sourceRectInWindow.height() <= 0) {
			onCaptured(null)
			return
		}
		val draw = {
			val bitmap = runCatching {
				Bitmap.createBitmap(
					readerPageTurnAnimationBitmapDimension(sourceRectInWindow.width(), bitmapQuality),
					readerPageTurnAnimationBitmapDimension(sourceRectInWindow.height(), bitmapQuality),
					Bitmap.Config.ARGB_8888
				)
			}.getOrNull()
			if (bitmap == null) {
				onCaptured(null)
			} else {
				val location = IntArray(2)
				webView.getLocationInWindow(location)
				val canvas = Canvas(bitmap)
				canvas.drawColor(backgroundColor)
				canvas.scale(
					bitmap.width / sourceRectInWindow.width().toFloat(),
					bitmap.height / sourceRectInWindow.height().toFloat()
				)
				canvas.translate(
					-(sourceRectInWindow.left - location[0]).toFloat(),
					-(sourceRectInWindow.top - location[1]).toFloat()
				)
				webView.draw(canvas)
				bitmap.setHasAlpha(false)
				bitmap.setPremultiplied(true)
				onCaptured(bitmap)
			}
		}
		val awaitCompositedPreview = {
			if (!webView.isAttachedToWindow) {
				onCaptured(null)
			} else {
				webView.postVisualStateCallback(
					visualStateRequestId.incrementAndGet(),
					object : WebView.VisualStateCallback() {
						override fun onComplete(requestId: Long) {
							if (!webView.isAttachedToWindow) onCaptured(null)
							else webView.postOnAnimation(draw)
						}
					}
				)
			}
		}
		if (Looper.myLooper() == Looper.getMainLooper()) awaitCompositedPreview() else mainHandler.post(awaitCompositedPreview)
	}

	fun invalidatePage(pageIndex: Int, reason: String) {
		if (frozenSnapshotCacheDomain != null) return
		val removed = snapshotCache.entries
			.filter { (key, _) -> key.visualPageIndex == pageIndex }
			.map { it.key to it.value }
		removed.forEach { (key, snapshot) ->
			removeCachedSnapshot(key, snapshot)?.releaseCacheOwnership()
		}
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Page-turn snapshot page cleared page=$pageIndex reason=$reason removed=${removed.size} entries=${snapshotCache.keys}"
		)
	}

	private fun invalidatePublications() {
		publicationLedger.invalidate()
		publicationScheduler.cancelBeforeEpoch(publicationLedger.currentEpoch())
	}

	fun invalidate(reason: String) {
		if (frozenSnapshotCacheDomain != null) return
		synchronized(closeFenceLock) { activeGeneration += 1 }
		try {
			pendingDescriptorOwners.cancelAll()
		} catch (failure: Throwable) {
			publicationLedger.recordFailure(failure)
		}
		invalidatePublications()
		physicalLayoutAuthority = null
		protectedSnapshotPageIndices = emptySet()
		protectedEncodedCenterPageIndex = null
		protectedEncodedPageIndices = emptySet()
		protectedEncodedProfile = null
		rasterCache?.protectDecodedPageIndices(emptySet())
		rasterCache?.clearEncodedWindowProtection()
		val descriptorState = synchronized(descriptorOwnershipAdmissionLock) {
			val requests = descriptorRequests.values.toList()
			val recipients = (
				descriptorPhysicalRecipients.values +
					requests.flatMap { request -> request.recipients.values }
			).distinctBy(ReaderPageRasterHydrationRecipient::token)
			descriptorRequests.clear()
			descriptorRequestTokens.clear()
			rasterDescriptors.clear()
			requests to recipients
		}
		descriptorState.first.forEach(::completeDescriptorRequest)
		descriptorState.second.forEach { recipient ->
			try {
				deliverHydrationResult(recipient.callback, null)
			} finally {
				completeDescriptorRecipient(recipient)
			}
		}
		val hydrations = synchronized(hydrationOwnershipAdmissionLock) {
			inFlightRasterHydrations.values.toList().also {
				inFlightRasterHydrations.clear()
			}
		}
		hydrations.forEach { hydration -> hydration.job?.cancel() }
		hydrations.flatMap { hydration -> hydration.recipients.values }
			.forEach { recipient ->
				try {
					deliverHydrationResult(recipient.callback, null)
				} finally {
					completeHydrationRecipient(recipient)
				}
			}
		hydrations.filter { hydration -> hydration.job == null }
			.forEach(::completeHydration)
		snapshotCache.values.distinctBy { System.identityHashCode(it) }.forEach { it.releaseCacheOwnership() }
		snapshotCache.clear()
		snapshotCacheTokens.clear()
		frozenSnapshotCacheEntries.values.forEach { it.snapshot.release() }
		frozenSnapshotCacheEntries.clear()
		frozenSnapshotCacheDomain = null
		snapshotDurability.clear()
		snapshotExactRasterIdentities.clear()
		Logger.i(ReaderPageTurnBundleSourceTag, "Page-turn snapshot cache cleared reason=$reason")
	}

	private fun registerTeardownPhysicalOwner() {
		val descriptor = ReaderExactPhysicalOwnerDescriptor(
			kind = ReaderTransitionResourceKind.Raster,
			state = ReaderLegacyResourceState.Running
		)
		val physicalOwner = synchronized(teardownOwnershipLock) {
			if (teardownOwnershipRegistered) return
			val frozenDomain = frozenSnapshotCacheDomain
			val admitted = if (frozenDomain == null) {
				teardownOwnership.admit(descriptor)
			} else {
				teardownOwnership.admitLateDiscoveredDuringFrozenEpoch(
					frozenDomain,
					descriptor
				)
			}
			if (admitted != null) {
				teardownPhysicalOwner = admitted
				teardownOwnershipRegistered = true
			}
			admitted
		}
		checkNotNull(physicalOwner) {
			"Mandatory reader teardown must have exact physical ownership"
		}
	}

	fun fenceForClose() {
		val closeActions = synchronized(closeFenceLock) {
			if (closed) return
			closed = true
			persistenceRetryCorrelations.clear()
			activeLiveValidations.toList() to (frozenSnapshotCacheDomain != null)
		}
		val (liveValidations, frozen) = closeActions
		var failure = closeInvalidationFailure
		fun captureCloseFailure(action: () -> Unit) {
			try {
				action()
			} catch (next: Throwable) {
				val first = failure
				if (first == null) failure = next
				else if (next !== first) first.addSuppressed(next)
			}
		}
		try {
			val terminalRequests = synchronized(rasterInitializationFenceLock) {
				rasterInitializationRestarts.clear()
				rasterPersistenceRequests.filter { !it.attemptActive }
			}
			terminalRequests.forEach { request ->
				captureCloseFailure { completeRasterPersistenceRequest(request, ReaderPageRasterPublicationCompletion(ReaderPageRasterPublicationResult.Failed)) }
			}
			if (frozen) captureCloseFailure(::invalidatePublications)
			liveValidations.forEach { validation ->
				captureCloseFailure { validation.cancel() }
			}
			captureCloseFailure { rasterJob.cancel() }
			captureCloseFailure { pendingDescriptorOwners.close() }
			if (!frozen) captureCloseFailure { invalidate("close") }
		} finally {
			closeInvalidationFailure = failure
			closeFenceCompletion.complete(Unit)
		}
	}

	fun close(): Deferred<Unit> {
		registerTeardownPhysicalOwner()
		fenceForClose()
		closeCompletion.start()
		return closeCompletion
	}

	suspend fun closeAndJoin() {
		close().await()
	}

	private fun restoreLiveComposition(
		webView: WebView,
		token: String,
		mutationGeneration: ReaderForegroundWebViewMutationGeneration,
		isStillCurrent: () -> Boolean,
		onRestored: (Boolean) -> Unit = {}
	) {
		if (!webView.isAttachedToWindow || !isStillCurrent()) {
			onRestored(false)
			return
		}
		val quotedToken = JSONObject.quote(token)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.restorePageTurnLiveComposition?.(" +
				"$quotedToken, ${mutationGeneration.value})"
		) { restored ->
			if (
				!restored.isJavascriptTrue() ||
				!webView.isAttachedToWindow ||
				!isStillCurrent()
			) {
				onRestored(false)
				return@evaluateJavascript
			}
			webView.postVisualStateCallback(
				visualStateRequestId.incrementAndGet(),
				object : WebView.VisualStateCallback() {
					override fun onComplete(requestId: Long) {
						if (!webView.isAttachedToWindow || !isStillCurrent()) {
							onRestored(false)
						} else {
							webView.postOnAnimation {
								onRestored(webView.isAttachedToWindow && isStillCurrent())
							}
						}
					}
				}
			)
		}
	}
}

internal fun readerPageTurnOpaqueColor(argb: Long?): Int {
	val color = argb?.toInt() ?: Color.rgb(234, 217, 174)
	return color or Color.BLACK
}

internal fun readerPageTurnAnimationBitmapDimension(
	physicalPixels: Int,
	quality: ReaderPageBitmapQuality
): Int = (physicalPixels * quality.scale).roundToInt().coerceAtLeast(1)

internal fun readerPageSlideSnapshotWindow(
	centerPageIndex: Int,
	step: Int,
	pageCount: Int
): List<Int> = listOf(
	centerPageIndex,
	centerPageIndex + step,
	centerPageIndex + (2 * step),
	centerPageIndex - step,
	centerPageIndex - (2 * step)
).filter { it in 0 until pageCount }.distinct()

private fun String?.isJavascriptTrue(): Boolean = runCatching {
	JSONTokener(orEmpty()).nextValue() as? Boolean == true
}.getOrDefault(false)
