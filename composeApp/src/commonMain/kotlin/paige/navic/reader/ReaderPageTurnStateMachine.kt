package paige.navic.reader

import kotlin.math.abs

enum class ReaderPageTurnPhase { Idle, Capturing, Deforming, Committing, Relaxing }

sealed interface ReaderPageTurnEffect {
	data object AttachOverlay : ReaderPageTurnEffect
	data class Render(val progress: Float) : ReaderPageTurnEffect
	data class AnimateCommit(val fromProgress: Float) : ReaderPageTurnEffect
	data class AnimateRelax(val fromProgress: Float) : ReaderPageTurnEffect
	data class Commit(val direction: ReaderPageTurnPhysicalDirection) : ReaderPageTurnEffect
	data object DetachOverlay : ReaderPageTurnEffect
}

class ReaderPageTurnStateMachine(
	private val distanceCommitThreshold: Float = 0.34f,
	private val velocityCommitThresholdPxPerSecond: Float = 900f
) {
	var phase: ReaderPageTurnPhase = ReaderPageTurnPhase.Idle
		private set
	var direction: ReaderPageTurnPhysicalDirection = ReaderPageTurnPhysicalDirection.TowardLeft
		private set
	var spread: Boolean = false
		private set
	var progress: Float = 0f
		private set

	private var generation: Long = 0
	private var peakProgress: Float = 0f
	private var lastDeltaAxis: Float = 0f
	private var lastTimestampMs: Long? = null
	private var velocityPxPerSecond: Float = 0f
	private var pendingCommit: Boolean? = null
	private var overlayAttached: Boolean = false

	fun begin(direction: ReaderPageTurnPhysicalDirection, spread: Boolean): Long {
		generation += 1
		this.direction = direction
		this.spread = spread
		phase = ReaderPageTurnPhase.Capturing
		progress = 0f
		peakProgress = 0f
		lastDeltaAxis = 0f
		lastTimestampMs = null
		velocityPxPerSecond = 0f
		pendingCommit = null
		overlayAttached = false
		return generation
	}

	fun update(deltaAxis: Float, axisSize: Int, timestampMs: Long): List<ReaderPageTurnEffect> {
		if (phase == ReaderPageTurnPhase.Idle || phase == ReaderPageTurnPhase.Committing || phase == ReaderPageTurnPhase.Relaxing) return emptyList()
		updateMotion(deltaAxis, axisSize, timestampMs)
		return if (phase == ReaderPageTurnPhase.Deforming) {
			listOf(ReaderPageTurnEffect.Render(progress))
		} else {
			emptyList()
		}
	}

	fun release(deltaAxis: Float, axisSize: Int, timestampMs: Long): List<ReaderPageTurnEffect> {
		if (phase != ReaderPageTurnPhase.Capturing && phase != ReaderPageTurnPhase.Deforming) return emptyList()
		updateMotion(deltaAxis, axisSize, timestampMs)
		val commit = peakProgress >= distanceCommitThreshold || velocityPxPerSecond >= velocityCommitThresholdPxPerSecond
		if (phase == ReaderPageTurnPhase.Capturing) {
			pendingCommit = commit
			return emptyList()
		}
		return beginTerminalAnimation(commit)
	}

	fun captureSucceeded(captureGeneration: Long): List<ReaderPageTurnEffect> {
		if (captureGeneration != generation || phase != ReaderPageTurnPhase.Capturing) return emptyList()
		overlayAttached = true
		val effects = mutableListOf<ReaderPageTurnEffect>(
			ReaderPageTurnEffect.AttachOverlay,
			ReaderPageTurnEffect.Render(progress)
		)
		val deferredCommit = pendingCommit
		if (deferredCommit == null) {
			phase = ReaderPageTurnPhase.Deforming
		} else {
			effects += beginTerminalAnimation(deferredCommit)
		}
		return effects
	}

	fun captureFailed(captureGeneration: Long): List<ReaderPageTurnEffect> {
		if (captureGeneration != generation || phase != ReaderPageTurnPhase.Capturing) return emptyList()
		reset(invalidateGeneration = true)
		return emptyList()
	}

	fun cancel(): List<ReaderPageTurnEffect> {
		if (phase == ReaderPageTurnPhase.Idle) return emptyList()
		val effects = if (overlayAttached) listOf(ReaderPageTurnEffect.DetachOverlay) else emptyList()
		reset(invalidateGeneration = true)
		return effects
	}

	fun animationFinished(): List<ReaderPageTurnEffect> = when (phase) {
		ReaderPageTurnPhase.Committing -> {
			val committedDirection = direction
			reset(invalidateGeneration = true)
			listOf(ReaderPageTurnEffect.Commit(committedDirection), ReaderPageTurnEffect.DetachOverlay)
		}
		ReaderPageTurnPhase.Relaxing -> {
			reset(invalidateGeneration = true)
			listOf(ReaderPageTurnEffect.DetachOverlay)
		}
		else -> emptyList()
	}

	private fun beginTerminalAnimation(commit: Boolean): List<ReaderPageTurnEffect> {
		pendingCommit = null
		return if (commit) {
			phase = ReaderPageTurnPhase.Committing
			listOf(ReaderPageTurnEffect.AnimateCommit(progress))
		} else {
			phase = ReaderPageTurnPhase.Relaxing
			listOf(ReaderPageTurnEffect.AnimateRelax(progress))
		}
	}

	private fun updateMotion(deltaAxis: Float, axisSize: Int, timestampMs: Long) {
		if (axisSize <= 0) return
		progress = (abs(deltaAxis) / axisSize.toFloat()).coerceIn(0f, 1f)
		peakProgress = maxOf(peakProgress, progress)
		lastTimestampMs?.let { previousTimestamp ->
			val elapsedMs = timestampMs - previousTimestamp
			if (elapsedMs > 0) {
				velocityPxPerSecond = maxOf(
					velocityPxPerSecond,
					abs(deltaAxis - lastDeltaAxis) * 1000f / elapsedMs
				)
			}
		}
		lastDeltaAxis = deltaAxis
		lastTimestampMs = timestampMs
	}

	private fun reset(invalidateGeneration: Boolean) {
		if (invalidateGeneration) generation += 1
		phase = ReaderPageTurnPhase.Idle
		progress = 0f
		peakProgress = 0f
		lastTimestampMs = null
		velocityPxPerSecond = 0f
		pendingCommit = null
		overlayAttached = false
	}
}
