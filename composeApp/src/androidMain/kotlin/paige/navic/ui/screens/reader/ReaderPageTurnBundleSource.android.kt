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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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
import paige.navic.reader.readerPageRasterStorageRoot
import paige.navic.util.core.Logger
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

private const val ReaderPageTurnBundleSourceTag = "ReaderPageTurnBundleSource"
private const val MaxCachedSnapshots = 5
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
	val publicationFence: () -> Boolean,
	val callback: (ReaderPageSlideSnapshot?) -> Unit
)

private data class ReaderPageRasterDescriptorIdentity(
	val generation: Long,
	val quality: ReaderPageBitmapQuality,
	val pageIndex: Int,
	val kind: ReaderPageTurnTransitionKind,
	val physicalLayout: ReaderPageRasterPhysicalLayout,
	val physicalLayoutEpoch: Long
)

private class ReaderPageRasterDescriptorRequest(
	val token: Long,
	val identity: ReaderPageRasterDescriptorIdentity,
	val webView: WeakReference<WebView>,
	val recipients: MutableMap<Long, ReaderPageRasterHydrationRecipient>
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
	var job: Job? = null
)

internal data class ReaderPageRasterPublicationValue<T : Any>(
	val key: ReaderPageRasterKey,
	val generation: ReaderPageRasterGeneration<T>
)

internal data class ReaderPageTurnBundleOwnershipMetrics(
	val rasterCache: ReaderPageRasterCacheMetrics,
	val stagedPublications: Int,
	val stagedPublicationLimit: Int,
	val pendingPublicationCallbacks: Int,
	val pendingPublicationCallbackLimit: Int
)

internal class ReaderPageTurnBundleSource(
	private val bitmapSource: ReaderPageTurnBitmapSource = ReaderPageTurnBitmapSource(),
	private val mainHandler: Handler = Handler(Looper.getMainLooper()),
	private val descriptorPort: ReaderPageRasterDescriptorPort =
		ReaderPageWebViewRasterDescriptorPort(),
	private val hydrationStorePort: ReaderPageRasterHydrationStorePort? = null,
	private val diagnostics: ReaderPageRuntimeDiagnostics? = null,
	private val qaFaultRegistry: ReaderPageQaFaultRegistry? = null,
	private val onOwnershipMutated: () -> Unit = {}
) {
	private var activeGeneration = 0L
	private var bitmapQuality = ReaderPageBitmapQuality.Balanced
	private val rasterJob = SupervisorJob()
	private val rasterScope = CoroutineScope(rasterJob + Dispatchers.Main.immediate)
	private val teardownJob = SupervisorJob()
	private val teardownScope = CoroutineScope(teardownJob + Dispatchers.Default)
	private val closeFenceLock = Any()
	private val activeLiveValidations =
		linkedSetOf<ReaderPageRelocationContentValidationHandle>()
	private val rasterInitializationMutex = Mutex()
	private val rasterPersistenceJobLock = Any()
	private val rasterPersistenceJobs = linkedSetOf<Job>()
	private val pendingDescriptorOwners = ReaderPagePendingCallbackOwners<ReaderPageSlideSnapshot>(
		retain = ReaderPageSlideSnapshot::retain,
		release = ReaderPageSlideSnapshot::release
	)
	private val visualStateRequestId = AtomicLong()
	private val persistenceAttemptIds = AtomicLong()
	private val rasterPhysicalLayoutEpoch = AtomicLong()
	private var physicalLayoutAuthority: ReaderPageRasterPhysicalLayoutAuthority? = null
	private val snapshotCache = LinkedHashMap<ReaderPageSlideSnapshotKey, ReaderPageSlideSnapshot>(0, 0.75f, true)
	private val snapshotDurability =
		IdentityHashMap<ReaderPageSlideSnapshot, ReaderPageRasterHydrationDurability>()
	private val descriptorRequests =
		mutableMapOf<Long, ReaderPageRasterDescriptorRequest>()
	private val descriptorRequestTokens =
		mutableMapOf<ReaderPageRasterDescriptorIdentity, Long>()
	private val rasterDescriptors =
		linkedMapOf<ReaderPageRasterDescriptorIdentity, ReaderPageRasterDescriptor>()
	private val inFlightRasterHydrations =
		mutableMapOf<ReaderPageRasterHydrationIdentity, InFlightRasterHydration>()
	private val hydrationScheduler = ReaderPageRasterHydrationScheduler(
		scope = rasterScope,
		maxConcurrentWorkers = 2
	)
	private var nextHydrationToken = 0L
	private val publicationScheduler = ReaderPageRasterPublicationScheduler(
		scope = rasterScope,
		maxConcurrentWorkers = 1
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
			release = { value ->
				ReaderAndroidPageRasterCodec.release(value.generation.value)
			}
		)
	private val publicationCompletionResults =
		ConcurrentHashMap<
			ReaderPageRasterPublicationRequest,
			ReaderPageRasterPublicationResult
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
	private var rasterScheduler: ReaderPageRasterScheduler<Bitmap>? = null
	private var activeWebView = WeakReference<WebView>(null)
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
			persistentStore = null
			rasterCache = null
			activeWebView.clear()
		},
		onFinished = {
			teardownJob.complete()
		}
	)
	val isAvailable: Boolean
		get() = bitmapSource.isAvailable

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
		withContext(Dispatchers.Main.immediate) {
			requireRasterInitializationOpen()
			rasterScheduler(webView)
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
		protectedSnapshotPageIndices = pageIndices.filterTo(linkedSetOf()) { it >= 0 }
		trimSnapshotCacheToCapacity()
		rasterCache?.protectDecodedPageIndices(protectedSnapshotPageIndices)
	}

	fun hasSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	): Boolean = cachedSnapshot(pageIndex, kind) != null

	fun retainedReferenceSnapshot(
		preferredPageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	): ReaderPageSlideSnapshot? = (
		cachedSnapshot(preferredPageIndex, kind)
			?: snapshotCache.entries.lastOrNull { (key, _) -> key.kind == kind }?.value
	).also { snapshot -> snapshot?.retain() }

	private fun removeCachedSnapshot(
		key: ReaderPageSlideSnapshotKey,
		expected: ReaderPageSlideSnapshot? = null
	): ReaderPageSlideSnapshot? {
		val cached = snapshotCache[key] ?: return null
		if (expected != null && cached !== expected) return null
		val removed = snapshotCache.remove(key) ?: return null
		snapshotDurability.remove(removed)
		return removed
	}

	fun trimMemory(reason: String) {
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
	): ReaderPageSlideSnapshot? = cachedSnapshot(pageIndex, kind)?.also { snapshot -> snapshot.retain() }

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
			closed ||
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
		publicationFence: () -> Boolean,
		onHydrated: (ReaderPageSlideSnapshot?) -> Unit
	): ReaderPageRasterHydrationRequest {
		val physicalLayoutEpoch = admitPhysicalLayout(kind, physicalLayout) ?: run {
			onHydrated(null)
			return ReaderPageRasterHydrationRequest { }
		}
		val recipientToken = Math.incrementExact(nextHydrationToken)
		nextHydrationToken = recipientToken
		val recipient = ReaderPageRasterHydrationRecipient(
			token = recipientToken,
			publicationFence = publicationFence,
			callback = onHydrated
		)
		val identity = ReaderPageRasterDescriptorIdentity(
			generation = activeGeneration,
			quality = bitmapQuality,
			pageIndex = pageIndex,
			kind = kind,
			physicalLayout = physicalLayout,
			physicalLayoutEpoch = physicalLayoutEpoch
		)
		val existing = descriptorRequestTokens[identity]
			?.let(descriptorRequests::get)
		if (existing != null) {
			existing.recipients[recipientToken] = recipient
		} else {
			val descriptorToken = Math.incrementExact(nextHydrationToken)
			nextHydrationToken = descriptorToken
			val request = ReaderPageRasterDescriptorRequest(
				token = descriptorToken,
				identity = identity,
				webView = WeakReference(webView),
				recipients = linkedMapOf(recipientToken to recipient)
			)
			descriptorRequests[descriptorToken] = request
			descriptorRequestTokens[identity] = descriptorToken
			val cached = rasterDescriptors[identity]
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
		return ReaderPageRasterHydrationRequest {
			dispatchToMain { cancelHydrationRecipient(recipientToken) }
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
			descriptor.visualPageOrdinal != identity.pageIndex
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
		val request = descriptorRequests.remove(requestToken)
			?.takeIf { candidate -> candidate.token == requestToken }
			?: return@dispatchToMain
		descriptorRequestTokens[request.identity]
			?.takeIf { token -> token == requestToken }
			?.let { descriptorRequestTokens.remove(request.identity) }
		val recipients = request.recipients.values.toList()
		val webView = request.webView.get()
		if (
			descriptor == null ||
			descriptor.visualPageOrdinal != request.identity.pageIndex ||
			closed ||
			request.identity.generation != activeGeneration ||
			request.identity.quality != bitmapQuality ||
			request.identity.physicalLayoutEpoch != rasterPhysicalLayoutEpoch.get() ||
			webView?.isAttachedToWindow != true
		) {
			recipients.forEach { recipient ->
				deliverHydrationResult(recipient.callback, null)
			}
			return@dispatchToMain
		}
		val currentRecipients = recipients.filter { recipient ->
			runCatching(recipient.publicationFence).getOrDefault(false)
		}
		(recipients - currentRecipients.toSet()).forEach { recipient ->
			deliverHydrationResult(recipient.callback, null)
		}
		if (currentRecipients.isEmpty()) return@dispatchToMain
		cacheRasterDescriptor(request.identity, descriptor)
		val key = descriptor.key(request.identity.quality)
		stageEncodedWindowProtection(key.profile)
		val hydrationIdentity = ReaderPageRasterHydrationIdentity(
			rasterIdentity = key.identity,
			kind = request.identity.kind,
			physicalLayout = request.identity.physicalLayout,
			physicalLayoutEpoch = request.identity.physicalLayoutEpoch
		)
		inFlightRasterHydrations[hydrationIdentity]
			?.takeIf { hydration ->
				hydration.generation == request.identity.generation &&
					hydration.quality == request.identity.quality
			}
			?.let { hydration ->
				currentRecipients.forEach { recipient ->
					hydration.recipients[recipient.token] = recipient
				}
				return@dispatchToMain
			}
		val hydrationToken = Math.incrementExact(nextHydrationToken)
		nextHydrationToken = hydrationToken
		val hydration = InFlightRasterHydration(
			token = hydrationToken,
			identity = hydrationIdentity,
			generation = request.identity.generation,
			quality = request.identity.quality,
			key = key,
			kind = request.identity.kind,
			webView = WeakReference(webView),
			recipients = currentRecipients.associateByTo(linkedMapOf()) { it.token }
		)
		inFlightRasterHydrations[hydrationIdentity] = hydration
		val job = hydrationScheduler.schedule { runPersistentHydration(hydration) }
		if (job == null) {
			if (inFlightRasterHydrations[hydrationIdentity] === hydration) {
				inFlightRasterHydrations.remove(hydrationIdentity)
				hydration.recipients.values.forEach { recipient ->
					deliverHydrationResult(recipient.callback, null)
				}
			}
		} else if (inFlightRasterHydrations[hydrationIdentity] === hydration) {
			hydration.job = job
		} else {
			job.cancel()
		}
	}

	private fun failDescriptorRequest(requestToken: Long) = dispatchToMain {
		val request = descriptorRequests.remove(requestToken) ?: return@dispatchToMain
		descriptorRequestTokens[request.identity]
			?.takeIf { token -> token == requestToken }
			?.let { descriptorRequestTokens.remove(request.identity) }
		request.recipients.values.forEach { recipient ->
			deliverHydrationResult(recipient.callback, null)
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
				if (inFlightRasterHydrations[hydration.identity] !== hydration) {
					return@withContext
				}
				val sourceCurrent = !closed &&
					hydration.generation == activeGeneration &&
					hydration.quality == bitmapQuality &&
					hydration.identity.physicalLayoutEpoch == rasterPhysicalLayoutEpoch.get() &&
					hydration.webView.get()?.isAttachedToWindow == true
				inFlightRasterHydrations.remove(hydration.identity)
				val recipients = hydration.recipients.values.toList()
				val eligible = recipients.filter { recipient ->
					sourceCurrent &&
						runCatching(recipient.publicationFence).getOrDefault(false)
				}
				(recipients - eligible.toSet()).forEach { recipient ->
					deliverHydrationResult(recipient.callback, null)
				}
				if (eligible.isEmpty()) return@withContext
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
				eligible.forEach { snapshot.retain() }
				val cached = putSnapshot(
					snapshot = snapshot,
					priority = ReaderPageRasterPriority.NextTransition,
					persist = false
				)
				markCachedSnapshotDurable(cached)
				rasterOwnershipTransferred = true
				if (cached !== snapshot) {
					eligible.forEach { cached.retain() }
					eligible.forEach { snapshot.release() }
				}
				eligible.forEach { recipient ->
					deliverHydrationResult(recipient.callback, cached)
				}
			}
		} finally {
			if (!rasterOwnershipTransferred) {
				raster?.value?.let(ReaderAndroidPageRasterCodec::release)
			}
			withContext(NonCancellable + Dispatchers.Main.immediate) {
				if (inFlightRasterHydrations[hydration.identity] === hydration) {
					inFlightRasterHydrations.remove(hydration.identity)
					hydration.recipients.values.forEach { recipient ->
						deliverHydrationResult(recipient.callback, null)
					}
				}
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
			inFlightRasterHydrations[hydration.identity] === hydration

	private fun cancelHydrationRecipient(recipientToken: Long) {
		descriptorRequests.values.firstOrNull { request ->
			recipientToken in request.recipients
		}?.let { request ->
			request.recipients.remove(recipientToken)
			if (request.recipients.isEmpty()) {
				descriptorRequests.remove(request.token)
				descriptorRequestTokens[request.identity]
					?.takeIf { token -> token == request.token }
					?.let { descriptorRequestTokens.remove(request.identity) }
			}
			return
		}
		inFlightRasterHydrations.values.firstOrNull { hydration ->
			recipientToken in hydration.recipients
		}?.let { hydration ->
			hydration.recipients.remove(recipientToken)
			if (hydration.recipients.isEmpty()) {
				inFlightRasterHydrations.remove(hydration.identity)
				hydration.job?.cancel()
			}
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
	): Boolean = synchronized(closeFenceLock) {
		if (closed) false else activeLiveValidations.add(handle)
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
		val posted = mainHandler.post {
			val workerResult = ownership.awaitingResult() ?: return@post
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
				return@post
			}
			val finalFence = bitmapSource.confirmLivePresentationReceipt(
				webView = webView,
				target = target,
				acceptedReceipt = receipt,
				isStillCurrent = {
					synchronized(closeFenceLock) { !closed } &&
						runCatching(isStillCurrent).getOrDefault(false)
				}
			) { currentReceipt ->
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
			ownership.attachFinalFence(finalFence)
		}
		if (!posted) ownership.cancel()
	}

	fun validateLivePresentation(
		webView: WebView,
		rendererSurface: PageSurfaceView,
		request: ReaderPageRelocationRequest,
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
			if (!ownership.completeCapture(candidate = null, result = result)) return false
			return ownership.publish { _, _, _, completed -> onValidated(completed) }
		}
		if (!registerLiveValidation(ownership)) {
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
				textureGeneration = request.textureGeneration
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
				val workerJob = try {
					rasterScope.launch(Dispatchers.Default) {
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
				} catch (_: Throwable) {
					ownership.recordWorkerResult(
						ReaderPageRelocationContentValidationResult.Invalidated
					)
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
					return@captureLiveCompositedSurface
				}
				ownership.attachWorker(
					ReaderPageRelocationContentValidationHandle {
						workerJob.cancel()
						true
					}
				)
				workerJob.invokeOnCompletion { failure ->
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
				}
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
		isStillCurrent: () -> Boolean = { true },
		onStagingStarted: (ReaderPageSlideSnapshot, (Boolean) -> Unit) -> Unit,
		onCaptureFailed: () -> Unit,
		onCaptured: (ReaderPageRasterPublicationResult) -> Unit
	) {
		activeWebView = WeakReference(webView)
		val referenceLayout = readerPageRasterPhysicalLayout(reference)
		val physicalLayoutEpoch = referenceLayout?.let { layout ->
			admitPhysicalLayout(kind, layout)
		}
		val generation = activeGeneration
		if (
			closed ||
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
				isStillCurrent = isStillCurrent
			) { result ->
				if (isStillCurrent()) onCaptured(result)
			}
			return
		}
		capturePreparedPage(
			webView = webView,
			pageIndex = pageIndex,
			kind = kind,
			token = itemToken,
			previewGeneration = previewGeneration,
			generation = generation,
			isStillCurrent = isStillCurrent,
			onStagingStarted = { onPresented -> onStagingStarted(reference, onPresented) }
		) { captured ->
			if (
				captured == null ||
				!readerPageRasterPhysicalLayoutMatches(captured, reference) ||
				generation != activeGeneration ||
				physicalLayoutEpoch != rasterPhysicalLayoutEpoch.get() ||
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
				previewGeneration = previewGeneration
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
				"window.NavicReaderBridge?.exposePageTurnPreviewFinal?.($quotedToken) === true"
			) { encoded ->
				if (
					generation != activeGeneration ||
					!isStillCurrent() ||
					!encoded.isJavascriptTrue()
				) {
					restoreLiveComposition(webView, token) { _ -> onCaptured(null) }
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
								restoreLiveComposition(webView, token) { _ -> onCaptured(null) }
								return
							}
							webView.postOnAnimation frame@{
								if (!isStillCurrent()) {
									restoreLiveComposition(webView, token) { _ -> onCaptured(null) }
									return@frame
								}
								webView.evaluateJavascript(
									"window.NavicReaderBridge?." +
										"confirmPageTurnPreviewPresentation?.($quotedToken) === true"
								) confirmation@{ confirmed ->
									if (
										generation != activeGeneration ||
										!isStillCurrent() ||
										!confirmed.isJavascriptTrue()
									) {
										restoreLiveComposition(webView, token) { _ -> onCaptured(null) }
										return@confirmation
									}
									capturePreparedSurface(
										webView = webView,
										target = presentationTarget,
										isStillCurrent = {
											generation == activeGeneration && isStillCurrent()
										}
									) { captured ->
										restoreLiveComposition(webView, token) { restored ->
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
		generation: Long = activeGeneration
	): ReaderPageSlideSnapshot? = cacheSnapshot(pageIndex, kind, current, generation)

	fun ensurePersistentSnapshot(
		snapshot: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		isStillCurrent: () -> Boolean = { true },
		onPersisted: (ReaderPageRasterPublicationResult) -> Unit
	) {
		persistCachedSnapshot(
			snapshot = snapshot,
			priority = priority,
			isStillCurrent = isStillCurrent,
			onPersisted = onPersisted
		)
	}

	private fun cacheSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		current: ReaderPageTurnCaptureResult,
		generation: Long
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
		return putSnapshot(snapshot, ReaderPageRasterPriority.Current)
	}

	private fun cachedSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot? = null
	): ReaderPageSlideSnapshot? {
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
		isStillCurrent: () -> Boolean = { true },
		onPersisted: (ReaderPageRasterPublicationResult) -> Unit
	) {
		schedulePersistentSnapshot(
			snapshot = snapshot,
			priority = priority,
			isStillCurrent = isStillCurrent
		) { result ->
			if (!runCatching(isStillCurrent).getOrDefault(false)) return@schedulePersistentSnapshot
			if (result == ReaderPageRasterPublicationResult.Durable) {
				markCachedSnapshotDurable(snapshot)
			}
			onPersisted(result)
		}
	}

	private fun putSnapshot(
		snapshot: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		persist: Boolean = true,
		isStillCurrent: () -> Boolean = { true },
		onPersisted: (ReaderPageRasterPublicationResult) -> Unit = {}
	): ReaderPageSlideSnapshot {
		val physicalLayout = checkNotNull(readerPageRasterPhysicalLayout(snapshot))
		check(currentPhysicalLayoutEpoch(snapshot.key.kind, physicalLayout) != null) {
			"Cannot cache a page snapshot outside the active physical layout"
		}
		snapshotCache[snapshot.key]?.let { cached ->
			snapshot.releaseCacheOwnership()
			if (persist) {
				persistCachedSnapshot(cached, priority, isStillCurrent, onPersisted)
			}
			return cached
		}
		snapshotCache[snapshot.key] = snapshot
		snapshotDurability[snapshot] =
			ReaderPageRasterHydrationDurability.RequiresPublication
		if (persist) {
			persistCachedSnapshot(snapshot, priority, isStillCurrent, onPersisted)
		}
		trimSnapshotCacheToCapacity()
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Page-turn snapshot cached key=${snapshot.key} entries=${snapshotCache.keys}"
		)
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
		isStillCurrent: () -> Boolean = { true },
		onPersisted: (ReaderPageRasterPublicationResult) -> Unit = {}
	) {
		if (!runCatching(isStillCurrent).getOrDefault(false)) {
			onPersisted(ReaderPageRasterPublicationResult.Failed)
			return
		}
		val pageIndex = snapshot.key.visualPageIndex
		if (closed) {
			rasterPersistenceSkipped(
				pageIndex,
				"bundle-source-closed",
				activeGeneration
			)
			onPersisted(ReaderPageRasterPublicationResult.Failed)
			return
		}
		val webView = activeWebView.get()?.takeIf { it.isAttachedToWindow }
			?: run {
				rasterPersistenceSkipped(
					pageIndex,
					"webview-unavailable",
					activeGeneration
				)
				onPersisted(ReaderPageRasterPublicationResult.Failed)
				return
			}
		val generation = activeGeneration
		val physicalLayout = readerPageRasterPhysicalLayout(snapshot)
		val physicalLayoutEpoch = physicalLayout?.let { layout ->
			currentPhysicalLayoutEpoch(snapshot.key.kind, layout)
		}
		if (physicalLayout == null || physicalLayoutEpoch == null) {
			rasterPersistenceSkipped(pageIndex, "physical-layout-stale", generation)
			onPersisted(ReaderPageRasterPublicationResult.Failed)
			return
		}
		val descriptorOwner = pendingDescriptorOwners.acquire(snapshot) {
			onPersisted(ReaderPageRasterPublicationResult.Failed)
		} ?: run {
			rasterPersistenceSkipped(
				pageIndex,
				"bundle-source-closed",
				generation
			)
			onPersisted(ReaderPageRasterPublicationResult.Failed)
			return
		}
		try {
			webView.evaluateJavascript(
				"JSON.stringify(window.NavicReaderBridge?.pageTurnRasterDescriptor?.($pageIndex) ?? null)"
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
					onPersisted(ReaderPageRasterPublicationResult.Failed)
					return@callback
				}
				val descriptor = readerPageRasterDescriptor(encodedDescriptor)
					?: run {
						rasterPersistenceSkipped(
							pageIndex,
							"descriptor-unavailable",
							generation
						)
						onPersisted(ReaderPageRasterPublicationResult.Failed)
						return@callback
					}
				cacheRasterDescriptor(
					ReaderPageRasterDescriptorIdentity(
						generation = generation,
						quality = snapshot.key.bitmapQuality,
						pageIndex = pageIndex,
						kind = snapshot.key.kind,
						physicalLayout = physicalLayout,
						physicalLayoutEpoch = physicalLayoutEpoch
					),
					descriptor
				)
				val key = descriptor.key(snapshot.key.bitmapQuality)
				val persistentBitmap = runCatching {
					snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, false)
				}.getOrNull()
				if (persistentBitmap == null) {
					rasterPersistenceSkipped(
						pageIndex,
						"bitmap-copy-failed",
						generation
					)
					onPersisted(ReaderPageRasterPublicationResult.Failed)
					return@callback
				}
				val rasterGeneration = ReaderPageRasterGeneration(
					metadata = snapshot.toRasterMetadata(),
					value = persistentBitmap,
					captureMillis = snapshot.captureMillis.coerceAtLeast(0L)
				)
				val persistenceJob = rasterScope.launch {
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
							onPersisted(ReaderPageRasterPublicationResult.Failed)
							return@launch
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
							epoch = publicationEpoch
						)
						val publicationStartedAt = diagnostics?.now() ?: 0L
						val persistenceAttemptId = ReaderPagePersistenceAttemptId(
							persistenceAttemptIds.incrementAndGet()
						)
						var publicationQaFaultCorrelation:
							ReaderPageQaFaultCorrelation? = null
						val registration = publicationLedger.begin(
							digest = key.digest,
							value = value
						) { persisted ->
							val publicationResult = when {
								persisted -> ReaderPageRasterPublicationResult.Durable
								closed || publicationEpoch != publicationLedger.currentEpoch() ->
									ReaderPageRasterPublicationResult.Failed
								else -> publicationCompletionResults[publicationRequest]
									?: ReaderPageRasterPublicationResult.Failed
							}
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
							onPersisted(publicationResult)
						}
						publicationQaFaultCorrelation =
							readerPageRasterPublicationRetryCorrelation(
								registration,
								persistenceRetryCorrelations[key.digest]
							)
						publicationValueTransferred = true
						when (registration) {
							is ReaderPageRasterPublicationRegistration.Started -> {
								scheduleRasterPublication(
									request = registration.request,
									physicalLayoutEpoch = physicalLayoutEpoch,
									persistenceAttemptId = persistenceAttemptId,
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
							onPersisted(ReaderPageRasterPublicationResult.Failed)
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
							onPersisted(ReaderPageRasterPublicationResult.Failed)
						}
					} finally {
						if (!publicationValueTransferred) {
							ReaderAndroidPageRasterCodec.release(persistentBitmap)
						}
					}
				}
				trackRasterPersistenceJob(persistenceJob)
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

	private fun scheduleRasterPublication(
		request: ReaderPageRasterPublicationRequest,
		physicalLayoutEpoch: Long,
		persistenceAttemptId: ReaderPagePersistenceAttemptId,
		onQaFaultApplied: (ReaderPageQaFaultCorrelation) -> Unit
	) {
		publicationScheduler.schedule(request) {
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
								if (physicalLayoutEpoch == rasterPhysicalLayoutEpoch.get()) {
									publicationLedger.commitFence(request).commit(action)
								} else {
									ReaderPageRasterWriteResult(
										persisted = false,
										ownership = ReaderPageRasterValueOwnership.Caller
									)
								}
							}
							write = when {
								physicalLayoutEpoch != rasterPhysicalLayoutEpoch.get() -> write
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
				val physicalLayoutCurrent =
					physicalLayoutEpoch == rasterPhysicalLayoutEpoch.get()
				val persistedForCurrentLayout = write.persisted && physicalLayoutCurrent
				val publicationResult = when {
					persistedForCurrentLayout -> ReaderPageRasterPublicationResult.Durable
					physicalLayoutCurrent &&
						write.failureReason == ReaderPageRasterWriteFailureReason.DiskCapacity ->
						ReaderPageRasterPublicationResult.CapacityReached
					else -> ReaderPageRasterPublicationResult.Failed
				}
				publicationCompletionResults[request] = publicationResult
				val accepted = try {
					publicationLedger.complete(
						request = request,
						persisted = persistedForCurrentLayout
					)
				} finally {
					publicationCompletionResults.remove(request)
				}
				if (!accepted || !persistedForCurrentLayout) {
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

	private fun trackRasterPersistenceJob(job: Job) {
		synchronized(rasterPersistenceJobLock) {
			rasterPersistenceJobs += job
		}
		job.invokeOnCompletion {
			synchronized(rasterPersistenceJobLock) {
				rasterPersistenceJobs -= job
			}
		}
	}

	private suspend fun rasterScheduler(webView: WebView): ReaderPageRasterScheduler<Bitmap> =
		rasterInitializationMutex.withLock {
			rasterScheduler?.let { return@withLock it }
			var cache: ReaderPageRasterCache<Bitmap>? = null
			var store: ReaderPageRasterCacheStore<Bitmap>? = null
			var scheduler: ReaderPageRasterScheduler<Bitmap>? = null
			try {
				requireRasterInitializationOpen()
				withContext(Dispatchers.IO) {
					ReaderPageRasterCache(
						root = readerPageRasterStorageRoot(webView.context.applicationContext),
						codec = ReaderAndroidPageRasterCodec,
						onDiagnostic = { diagnostic ->
							Logger.w(ReaderPageTurnBundleSourceTag, "Page raster cache $diagnostic")
						},
						onOwnershipMutated = onOwnershipMutated
					).also { created -> cache = created }
				}
				requireRasterInitializationOpen()
				val createdCache = checkNotNull(cache)
				val createdStore = ReaderPageRasterCacheStore(createdCache)
				store = createdStore
				val createdScheduler = ReaderPageRasterScheduler(
					scope = rasterScope,
					store = createdStore,
					generator = ReaderPageRasterGenerator { null },
					release = ReaderAndroidPageRasterCodec::release
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
				rasterCache = createdCache
				persistentStore = createdStore
				rasterScheduler = createdScheduler
					onOwnershipMutated()
				createdScheduler
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

	private fun requireRasterInitializationOpen() {
		val fenced = synchronized(closeFenceLock) { closed }
		if (fenced || !rasterJob.isActive) {
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

	fun invalidate(reason: String) {
		synchronized(closeFenceLock) { activeGeneration += 1 }
		try {
			pendingDescriptorOwners.cancelAll()
		} catch (failure: Throwable) {
			publicationLedger.recordFailure(failure)
		}
		publicationLedger.invalidate()
		publicationScheduler.cancelBeforeEpoch(publicationLedger.currentEpoch())
		physicalLayoutAuthority = null
		protectedSnapshotPageIndices = emptySet()
		protectedEncodedCenterPageIndex = null
		protectedEncodedPageIndices = emptySet()
		protectedEncodedProfile = null
		rasterCache?.protectDecodedPageIndices(emptySet())
		rasterCache?.clearEncodedWindowProtection()
		val descriptorRecipients = descriptorRequests.values
			.flatMap { request -> request.recipients.values }
		descriptorRequests.clear()
		descriptorRequestTokens.clear()
		rasterDescriptors.clear()
		val hydrations = inFlightRasterHydrations.values.toList()
		inFlightRasterHydrations.clear()
		hydrations.forEach { hydration -> hydration.job?.cancel() }
		(descriptorRecipients + hydrations.flatMap { hydration -> hydration.recipients.values })
			.forEach { recipient -> deliverHydrationResult(recipient.callback, null) }
		snapshotCache.values.distinctBy { System.identityHashCode(it) }.forEach { it.releaseCacheOwnership() }
		snapshotCache.clear()
		snapshotDurability.clear()
		Logger.i(ReaderPageTurnBundleSourceTag, "Page-turn snapshot cache cleared reason=$reason")
	}

	fun fenceForClose() {
		synchronized(closeFenceLock) {
			if (closed) return
			closed = true
			activeLiveValidations.toList().forEach { validation -> validation.cancel() }
			persistenceRetryCorrelations.clear()
			rasterJob.cancel()
			try {
				pendingDescriptorOwners.close()
			} catch (failure: Throwable) {
				publicationLedger.recordFailure(failure)
			}
			try {
				invalidate("close")
			} catch (failure: Throwable) {
				closeInvalidationFailure = failure
			}
		}
	}

	fun close(): Deferred<Unit> {
		fenceForClose()
		return teardown.start()
	}

	suspend fun closeAndJoin() {
		close().await()
	}

	private fun restoreLiveComposition(
		webView: WebView,
		token: String,
		onRestored: (Boolean) -> Unit = {}
	) {
		if (!webView.isAttachedToWindow) {
			onRestored(false)
			return
		}
		val quotedToken = JSONObject.quote(token)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.restorePageTurnLiveComposition?.($quotedToken)"
		) { restored ->
			if (!restored.isJavascriptTrue() || !webView.isAttachedToWindow) {
				onRestored(false)
				return@evaluateJavascript
			}
			webView.postVisualStateCallback(
				visualStateRequestId.incrementAndGet(),
				object : WebView.VisualStateCallback() {
					override fun onComplete(requestId: Long) {
						if (!webView.isAttachedToWindow) {
							onRestored(false)
						} else {
							webView.postOnAnimation {
								if (webView.isAttachedToWindow) {
									onRestored(true)
								} else {
									onRestored(false)
								}
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
