package paige.navic.reader

import kotlin.math.abs

enum class ReaderPageTurnPhase { Idle, Preparing, Deforming, Committing, Settling, Relaxing }

sealed interface ReaderPageTurnEffect {
	data object AttachOverlay : ReaderPageTurnEffect
	data class Render(val progress: Float) : ReaderPageTurnEffect
	data class AnimateCommit(val fromProgress: Float) : ReaderPageTurnEffect
	data class AnimateRelax(val fromProgress: Float) : ReaderPageTurnEffect
	data class Commit(val direction: ReaderPageTurnPhysicalDirection) : ReaderPageTurnEffect
	data object ShowFinalBase : ReaderPageTurnEffect
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
	val pendingReleaseCommit: Boolean?
		get() = pendingCommit

	private var generation: Long = 0
	private var peakProgress: Float = 0f
	private var lastDeltaAxis: Float = 0f
	private var lastTimestampMs: Long? = null
	private var velocityPxPerSecond: Float = 0f
	private var pendingCommit: Boolean? = null
	private var overlayAttached: Boolean = false
	private var commitAnimationFinished = false
	private var destinationSettled = false
	private var targetPageIndex: Int? = null

	fun begin(
		direction: ReaderPageTurnPhysicalDirection,
		spread: Boolean,
		targetPageIndex: Int? = null
	): Long {
		generation += 1
		this.direction = direction
		this.spread = spread
		phase = ReaderPageTurnPhase.Preparing
		this.targetPageIndex = targetPageIndex
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
		if (
			phase == ReaderPageTurnPhase.Idle ||
			phase == ReaderPageTurnPhase.Committing ||
			phase == ReaderPageTurnPhase.Settling ||
			phase == ReaderPageTurnPhase.Relaxing
		) return emptyList()
		updateMotion(deltaAxis, axisSize, timestampMs)
		return if (phase == ReaderPageTurnPhase.Deforming) {
			listOf(ReaderPageTurnEffect.Render(progress))
		} else {
			emptyList()
		}
	}

	fun release(deltaAxis: Float, axisSize: Int, timestampMs: Long): List<ReaderPageTurnEffect> {
		if (phase != ReaderPageTurnPhase.Preparing && phase != ReaderPageTurnPhase.Deforming) return emptyList()
		updateMotion(deltaAxis, axisSize, timestampMs)
		val commit = peakProgress >= distanceCommitThreshold || velocityPxPerSecond >= velocityCommitThresholdPxPerSecond
		if (phase == ReaderPageTurnPhase.Preparing) {
			pendingCommit = commit
			return emptyList()
		}
		return beginTerminalAnimation(commit)
	}

	fun captureSucceeded(captureGeneration: Long): List<ReaderPageTurnEffect> {
		if (captureGeneration != generation || phase != ReaderPageTurnPhase.Preparing) return emptyList()
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

	fun setTargetPageIndex(preparationGeneration: Long, pageIndex: Int): Boolean {
		if (preparationGeneration != generation || phase != ReaderPageTurnPhase.Preparing || pageIndex < 0) return false
		targetPageIndex = pageIndex
		return true
	}

	fun captureFailed(captureGeneration: Long): Boolean {
		if (captureGeneration != generation || phase != ReaderPageTurnPhase.Preparing) return false
		reset(invalidateGeneration = true)
		return true
	}

	fun cancel(): List<ReaderPageTurnEffect> {
		if (phase == ReaderPageTurnPhase.Idle) return emptyList()
		val effects = if (overlayAttached) listOf(ReaderPageTurnEffect.DetachOverlay) else emptyList()
		reset(invalidateGeneration = true)
		return effects
	}

	fun animationFinished(): List<ReaderPageTurnEffect> = when (phase) {
		ReaderPageTurnPhase.Committing -> {
			commitAnimationFinished = true
			phase = ReaderPageTurnPhase.Settling
			buildList {
				add(ReaderPageTurnEffect.ShowFinalBase)
				if (destinationSettled) addAll(finishCommitIfReady())
			}
		}
		ReaderPageTurnPhase.Relaxing -> {
			reset(invalidateGeneration = true)
			listOf(ReaderPageTurnEffect.DetachOverlay)
		}
		else -> emptyList()
	}

	fun destinationSettled(pageIndex: Int? = null): List<ReaderPageTurnEffect> {
		if (phase != ReaderPageTurnPhase.Committing && phase != ReaderPageTurnPhase.Settling) return emptyList()
		if (targetPageIndex != null && pageIndex != targetPageIndex) return emptyList()
		destinationSettled = true
		return finishCommitIfReady()
	}

	private fun beginTerminalAnimation(commit: Boolean): List<ReaderPageTurnEffect> {
		pendingCommit = null
		return if (commit) {
			phase = ReaderPageTurnPhase.Committing
			commitAnimationFinished = false
			destinationSettled = false
			listOf(ReaderPageTurnEffect.Commit(direction), ReaderPageTurnEffect.AnimateCommit(progress))
		} else {
			phase = ReaderPageTurnPhase.Relaxing
			listOf(ReaderPageTurnEffect.AnimateRelax(progress))
		}
	}

	private fun finishCommitIfReady(): List<ReaderPageTurnEffect> {
		if (!commitAnimationFinished || !destinationSettled) return emptyList()
		reset(invalidateGeneration = true)
		return listOf(ReaderPageTurnEffect.DetachOverlay)
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
		commitAnimationFinished = false
		destinationSettled = false
		targetPageIndex = null
	}
}
