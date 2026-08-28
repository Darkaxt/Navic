package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import paige.navic.reader.ReaderWhispersyncCueMapGeometryReceipt
import paige.navic.reader.ReaderWhispersyncCueMapHoldOutcome
import paige.navic.reader.ReaderWhispersyncCueMapHoldTracker
import paige.navic.reader.ReaderWhispersyncCueMapState
import paige.navic.reader.ReaderWhispersyncCueMapViewportAnchor
import paige.navic.reader.viewportAnchors
import kotlin.math.max

internal class ReaderWhispersyncCueMapNativeView(context: Context) : View(context) {
	private val density = resources.displayMetrics.density
	private val markerRadius = 9f * density
	private val stateRingStep = 2.5f * density
	private val holdRingRadius = markerRadius + stateRingStep * 2f
	private val hitRadius = max(24f * density, holdRingRadius)
	private val tracker = ReaderWhispersyncCueMapHoldTracker(
		holdDurationMs = HoldDurationMs,
		touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
	)
	private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
	private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}
	private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.CENTER
		typeface = android.graphics.Typeface.DEFAULT_BOLD
	}
	private var geometryReceipt: ReaderWhispersyncCueMapGeometryReceipt? = null
	private var pendingSourceOrdinal: Int? = null
	private var completionPosted = false
	var onHoldOutcome: (sourceOrdinal: Int, outcome: ReaderWhispersyncCueMapHoldOutcome) -> Unit = { _, _ -> }
	var onSeekRequested: (sourceOrdinal: Int) -> Unit = {}

	private val completeHold = Runnable {
		completionPosted = false
		val sourceOrdinal = tracker.sourceOrdinal ?: return@Runnable
		if (tracker.advance(SystemClock.uptimeMillis()) == ReaderWhispersyncCueMapHoldOutcome.Completed) {
			onHoldOutcome(sourceOrdinal, ReaderWhispersyncCueMapHoldOutcome.Completed)
			onSeekRequested(sourceOrdinal)
			invalidate()
		}
	}

	init {
		isClickable = false
		isFocusable = false
		importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
	}

	fun setPresentation(state: ReaderWhispersyncCueMapState) {
		val nextReceipt = state.geometryReceipt.takeIf { state.enabled }
		val lifecycleChanged = geometryReceipt?.let { current ->
			nextReceipt == null ||
				current.revisionDigest != nextReceipt.revisionDigest ||
				current.presentationGeneration != nextReceipt.presentationGeneration ||
				current.destinationCommitIdentity != nextReceipt.destinationCommitIdentity
		} == true
		if (lifecycleChanged) abandonHold()
		geometryReceipt = nextReceipt
		pendingSourceOrdinal = state.requestedSourceOrdinal
			.takeIf { state.transportAcknowledgementPending }
		visibility = if (nextReceipt == null) GONE else VISIBLE
		invalidate()
	}

	fun hitTest(x: Float, y: Float): Int? = anchors()
		.asReversed()
		.firstOrNull { anchor ->
			val center = markerCenter(anchor)
			val deltaX = x - center.first
			val deltaY = y - center.second
			deltaX * deltaX + deltaY * deltaY <= hitRadius * hitRadius
		}
		?.sourceOrdinal

	fun dispatchCuePointerEvent(event: MotionEvent): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				val sourceOrdinal = hitTest(event.x, event.y) ?: return false
				if (!tracker.begin(sourceOrdinal, event.x, event.y, event.eventTime)) return false
				completionPosted = true
				postDelayed(completeHold, HoldDurationMs)
				invalidate()
			}

			MotionEvent.ACTION_MOVE -> {
				val sourceOrdinal = tracker.sourceOrdinal
				tracker.move(event.x, event.y)?.let { outcome ->
					removeCompletionCallback()
					if (sourceOrdinal != null) onHoldOutcome(sourceOrdinal, outcome)
					invalidate()
				}
			}

			MotionEvent.ACTION_UP -> {
				val sourceOrdinal = tracker.sourceOrdinal
				val outcome = tracker.release(event.eventTime)
				removeCompletionCallback()
				when {
					outcome == ReaderWhispersyncCueMapHoldOutcome.Completed && sourceOrdinal != null -> {
						onHoldOutcome(sourceOrdinal, outcome)
						onSeekRequested(sourceOrdinal)
					}
					outcome != null && sourceOrdinal != null -> onHoldOutcome(sourceOrdinal, outcome)
				}
				invalidate()
			}

			MotionEvent.ACTION_CANCEL,
			MotionEvent.ACTION_POINTER_DOWN -> cancelHold(ReaderWhispersyncCueMapHoldOutcome.CancelledPointer)
		}
		return true
	}

	fun cancelForChrome() {
		cancelHold(ReaderWhispersyncCueMapHoldOutcome.CancelledChromeInterception)
	}

	fun cancelForCurl() {
		cancelHold(ReaderWhispersyncCueMapHoldOutcome.CancelledCurlStart)
	}

	private fun cancelHold(outcome: ReaderWhispersyncCueMapHoldOutcome) {
		val sourceOrdinal = tracker.sourceOrdinal
		val terminal = tracker.cancel(outcome)
		removeCompletionCallback()
		if (sourceOrdinal != null && terminal != null) onHoldOutcome(sourceOrdinal, terminal)
		invalidate()
	}

	private fun abandonHold() {
		removeCompletionCallback()
		tracker.abandon()
	}

	private fun removeCompletionCallback() {
		if (!completionPosted) return
		removeCallbacks(completeHold)
		completionPosted = false
	}

	override fun onDetachedFromWindow() {
		abandonHold()
		super.onDetachedFromWindow()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val now = SystemClock.uptimeMillis()
		for (anchor in anchors()) drawMarker(canvas, anchor, now)
		if (tracker.active || pendingSourceOrdinal != null) postInvalidateOnAnimation()
	}

	private fun drawMarker(
		canvas: Canvas,
		anchor: ReaderWhispersyncCueMapViewportAnchor,
		nowMillis: Long
	) {
		val (centerX, centerY) = markerCenter(anchor)
		fillPaint.color = if (anchor.audioActive) AudioActiveFill else MappedFill
		canvas.drawCircle(centerX, centerY, markerRadius, fillPaint)
		strokePaint.pathEffect = null
		strokePaint.strokeWidth = density
		strokePaint.color = Color.WHITE
		canvas.drawCircle(centerX, centerY, markerRadius, strokePaint)

		if (anchor.renderedHighlight) {
			strokePaint.strokeWidth = 1.8f * density
			strokePaint.color = RenderedStroke
			canvas.drawCircle(centerX, centerY, markerRadius - stateRingStep, strokePaint)
		}
		if (anchor.prepared) {
			strokePaint.strokeWidth = 1.5f * density
			strokePaint.color = PreparedStroke
			canvas.drawCircle(centerX, centerY, markerRadius + stateRingStep, strokePaint)
		}
		if (anchor.requested) {
			strokePaint.strokeWidth = 1.7f * density
			strokePaint.color = RequestedStroke
			strokePaint.pathEffect = DashPathEffect(floatArrayOf(3f * density, 2f * density), 0f)
			canvas.drawCircle(centerX, centerY, markerRadius + stateRingStep * 1.5f, strokePaint)
			strokePaint.pathEffect = null
		}

		if (tracker.sourceOrdinal == anchor.sourceOrdinal) {
			strokePaint.strokeWidth = 2f * density
			strokePaint.color = Color.WHITE
			canvas.drawArc(
				centerX - holdRingRadius,
				centerY - holdRingRadius,
				centerX + holdRingRadius,
				centerY + holdRingRadius,
				-90f,
				360f * tracker.progress(nowMillis),
				false,
				strokePaint
			)
		}
		if (pendingSourceOrdinal == anchor.sourceOrdinal) {
			strokePaint.strokeWidth = 2f * density
			strokePaint.color = RequestedStroke
			val start = ((nowMillis % PendingRotationMs).toFloat() / PendingRotationMs * 360f) - 90f
			canvas.drawArc(
				centerX - holdRingRadius,
				centerY - holdRingRadius,
				centerX + holdRingRadius,
				centerY + holdRingRadius,
				start,
				110f,
				false,
				strokePaint
			)
		}

		val label = anchor.sourceOrdinal.toString()
		labelPaint.textSize = when (label.length) {
			1, 2 -> 8f * density
			3 -> 7f * density
			else -> 6f * density
		}
		val metrics = labelPaint.fontMetrics
		val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
		canvas.drawText(label, centerX, baseline, labelPaint)
	}

	private fun anchors(): List<ReaderWhispersyncCueMapViewportAnchor> =
		geometryReceipt?.viewportAnchors(width.toFloat(), height.toFloat()).orEmpty()

	private fun markerCenter(anchor: ReaderWhispersyncCueMapViewportAnchor): Pair<Float, Float> =
		(anchor.x + markerRadius).coerceIn(holdRingRadius, width - holdRingRadius) to
			(anchor.y - 2f * density).coerceIn(holdRingRadius, height - holdRingRadius)

	private companion object {
		const val HoldDurationMs = 1_000L
		const val PendingRotationMs = 800L
		val MappedFill = Color.argb(224, 34, 34, 34)
		val AudioActiveFill = Color.argb(245, 27, 138, 88)
		val PreparedStroke = Color.rgb(56, 189, 248)
		val RequestedStroke = Color.rgb(251, 191, 36)
		val RenderedStroke = Color.rgb(232, 121, 249)
	}
}
