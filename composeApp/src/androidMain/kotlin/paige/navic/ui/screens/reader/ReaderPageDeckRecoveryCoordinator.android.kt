package paige.navic.ui.screens.reader

import karacken.curl.DeckRejectionReason

internal enum class ReaderPageRasterDeferralReason {
	ContentNotReady,
	LayoutUnstable,
	PaginationNotReady,
	WebViewDetached,
	ReaderPaused
}

internal sealed interface ReaderPageRasterRepairResult {
	data class Repaired(
		val repairedPageIndices: Set<Int>,
		val centerOrdinal: Int,
		val rasterEpoch: Long
	) : ReaderPageRasterRepairResult

	data class Deferred(
		val reason: ReaderPageRasterDeferralReason
	) : ReaderPageRasterRepairResult

	data class Failed(val reason: String) : ReaderPageRasterRepairResult

	data object Cancelled : ReaderPageRasterRepairResult
}

internal fun readerPageRasterRepairedResult(
	repairedPageIndices: Set<Int>,
	centerOrdinal: Int,
	rasterEpoch: Long
): ReaderPageRasterRepairResult.Repaired = ReaderPageRasterRepairResult.Repaired(
	repairedPageIndices = repairedPageIndices.toSet(),
	centerOrdinal = centerOrdinal,
	rasterEpoch = rasterEpoch
)

internal sealed interface ReaderPageRecoveredDeckBuildResult {
	data class Built(val generationId: Long) : ReaderPageRecoveredDeckBuildResult

	data class Failed(val reason: String) : ReaderPageRecoveredDeckBuildResult

	data object Stale : ReaderPageRecoveredDeckBuildResult
}

internal interface ReaderPageDeckRecoveryHost {
	fun isCurrentRepairWindow(
		repairedPageIndices: Set<Int>,
		centerOrdinal: Int,
		rasterEpoch: Long
	): Boolean

	fun hasUsablePreparedActiveDeck(): Boolean

	fun requestRecoveredDeckBuild(
		requestId: Long,
		repairedPageIndices: Set<Int>,
		centerOrdinal: Int,
		rasterEpoch: Long,
		onBuilt: (ReaderPageRecoveredDeckBuildResult) -> Unit
	)

	fun cancelRecoveredDeckBuild(requestId: Long)

	fun currentRecoveredDeckRole(): ReaderDeckSubmissionRole

	fun submitRecoveredDeck(
		generationId: Long,
		role: ReaderDeckSubmissionRole
	)

	fun releaseRecoveredDeck(generationId: Long)

	fun cancelSubmittedRecoveredDeck(
		generationId: Long,
		role: ReaderDeckSubmissionRole
	)

	fun isPrepared(generationId: Long): Boolean
}

internal sealed interface ReaderPageDeckRecoveryState {
	data object Idle : ReaderPageDeckRecoveryState

	data class WaitingForBuild(
		val requestId: Long,
		val repairedPageIndices: Set<Int>,
		val centerOrdinal: Int,
		val rasterEpoch: Long
	) : ReaderPageDeckRecoveryState

	data class WaitingForPreparation(
		val generationId: Long,
		val role: ReaderDeckSubmissionRole
	) : ReaderPageDeckRecoveryState

	data class Ready(val generationId: Long) : ReaderPageDeckRecoveryState

	data class Failed(val reason: String) : ReaderPageDeckRecoveryState
}

internal class ReaderPageDeckRecoveryCoordinator(
	private val host: ReaderPageDeckRecoveryHost,
	private val onStateChanged: (ReaderPageDeckRecoveryState) -> Unit = {}
) {
	private var nextRequestId = 0L

	var state: ReaderPageDeckRecoveryState = ReaderPageDeckRecoveryState.Idle
		private set

	val canAcceptPointer: Boolean
		get() = host.hasUsablePreparedActiveDeck()

	fun accept(result: ReaderPageRasterRepairResult): Boolean {
		if (result !is ReaderPageRasterRepairResult.Repaired) return false
		if (!host.isCurrentRepairWindow(
				result.repairedPageIndices,
				result.centerOrdinal,
				result.rasterEpoch
			)
		) {
			return false
		}
		val requestId = Math.incrementExact(nextRequestId)
		nextRequestId = requestId
		val previous = state
		transitionTo(
			ReaderPageDeckRecoveryState.WaitingForBuild(
				requestId = requestId,
				repairedPageIndices = result.repairedPageIndices.toSet(),
				centerOrdinal = result.centerOrdinal,
				rasterEpoch = result.rasterEpoch
			)
		)
		cancelOwnedState(previous)
		try {
			host.requestRecoveredDeckBuild(
				requestId = requestId,
				repairedPageIndices = result.repairedPageIndices,
				centerOrdinal = result.centerOrdinal,
				rasterEpoch = result.rasterEpoch
			) { build -> onDeckBuilt(requestId, build) }
		} catch (failure: Throwable) {
			host.cancelRecoveredDeckBuild(requestId)
			val waiting = state as? ReaderPageDeckRecoveryState.WaitingForBuild
			if (waiting?.requestId == requestId) {
				transitionTo(
					ReaderPageDeckRecoveryState.Failed(
						failure.message ?: "protected-window-build-failed"
					)
				)
			}
			return false
		}
		return true
	}

	fun onDeckBuilt(
		requestId: Long,
		result: ReaderPageRecoveredDeckBuildResult
	): Boolean {
		val waiting = state as? ReaderPageDeckRecoveryState.WaitingForBuild
		if (waiting == null || waiting.requestId != requestId) {
			releaseIfUnsubmitted(result)
			return false
		}
		if (!host.isCurrentRepairWindow(
				waiting.repairedPageIndices,
				waiting.centerOrdinal,
				waiting.rasterEpoch
			)
		) {
			host.cancelRecoveredDeckBuild(requestId)
			releaseIfUnsubmitted(result)
			transitionTo(ReaderPageDeckRecoveryState.Idle)
			return false
		}
		when (result) {
			is ReaderPageRecoveredDeckBuildResult.Failed -> {
				transitionTo(ReaderPageDeckRecoveryState.Failed(result.reason))
				return false
			}
			ReaderPageRecoveredDeckBuildResult.Stale -> {
				transitionTo(ReaderPageDeckRecoveryState.Idle)
				return false
			}
			is ReaderPageRecoveredDeckBuildResult.Built -> Unit
		}
		val generationId = result.generationId
		val role = host.currentRecoveredDeckRole()
		transitionTo(
			ReaderPageDeckRecoveryState.WaitingForPreparation(
				generationId,
				role
			)
		)
		return try {
			host.submitRecoveredDeck(generationId, role)
			state is ReaderPageDeckRecoveryState.WaitingForPreparation ||
				state is ReaderPageDeckRecoveryState.Ready
		} catch (_: Throwable) {
			val owned = state as? ReaderPageDeckRecoveryState.WaitingForPreparation
			if (owned?.generationId == generationId && owned.role == role) {
				host.releaseRecoveredDeck(generationId)
				transitionTo(
					ReaderPageDeckRecoveryState.Failed(
						"recovered-deck-submission-failed"
					)
				)
			}
			false
		}
	}

	fun onDeckRejected(
		generationId: Long,
		reason: DeckRejectionReason
	): Boolean {
		val waiting = state as? ReaderPageDeckRecoveryState.WaitingForPreparation
			?: return false
		if (waiting.generationId != generationId) return false
		transitionTo(
			ReaderPageDeckRecoveryState.Failed(
				"recovered-deck-rejected:${reason.name}"
			)
		)
		return true
	}

	fun ownsSubmittedGeneration(generationId: Long): Boolean = when (val current = state) {
		is ReaderPageDeckRecoveryState.WaitingForPreparation ->
			current.generationId == generationId
		is ReaderPageDeckRecoveryState.Ready -> current.generationId == generationId
		else -> false
	}

	fun onDeckPreparationFailed(generationId: Long, reason: String): Boolean {
		if (!ownsSubmittedGeneration(generationId)) return false
		transitionTo(
			ReaderPageDeckRecoveryState.Failed(
				"recovered-deck-preparation-failed:$reason"
			)
		)
		return true
	}

	fun onDeckPrepared(generationId: Long): Boolean {
		val waiting = state as? ReaderPageDeckRecoveryState.WaitingForPreparation
			?: return false
		if (waiting.generationId != generationId || !host.isPrepared(generationId)) {
			return false
		}
		transitionTo(ReaderPageDeckRecoveryState.Ready(generationId))
		return true
	}

	fun onDeckReleased(generationId: Long): Boolean {
		return when (val current = state) {
			is ReaderPageDeckRecoveryState.WaitingForPreparation -> {
				if (current.generationId != generationId) return false
				transitionTo(
					ReaderPageDeckRecoveryState.Failed(
						"recovered-deck-released-before-preparation"
					)
				)
				true
			}
			is ReaderPageDeckRecoveryState.Ready -> {
				if (current.generationId != generationId) return false
				transitionTo(ReaderPageDeckRecoveryState.Idle)
				true
			}
			else -> false
		}
	}

	fun cancelAll() {
		val previous = state
		transitionTo(ReaderPageDeckRecoveryState.Idle)
		nextRequestId = Math.incrementExact(nextRequestId)
		cancelOwnedState(previous)
	}

	private fun releaseIfUnsubmitted(result: ReaderPageRecoveredDeckBuildResult) {
		if (result is ReaderPageRecoveredDeckBuildResult.Built) {
			host.releaseRecoveredDeck(result.generationId)
		}
	}

	private fun cancelOwnedState(previous: ReaderPageDeckRecoveryState) {
		when (previous) {
			is ReaderPageDeckRecoveryState.WaitingForBuild ->
				host.cancelRecoveredDeckBuild(previous.requestId)
			is ReaderPageDeckRecoveryState.WaitingForPreparation ->
				host.cancelSubmittedRecoveredDeck(
					previous.generationId,
					previous.role
				)
			else -> Unit
		}
	}

	private fun transitionTo(next: ReaderPageDeckRecoveryState) {
		if (state == next) return
		state = next
		onStateChanged(next)
	}
}
