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
		val rasterEpoch: Long,
		val diagnosticOperation: ReaderPageDiagnosticOperation? = null
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
	rasterEpoch: Long,
	diagnosticOperation: ReaderPageDiagnosticOperation? = null
): ReaderPageRasterRepairResult.Repaired = ReaderPageRasterRepairResult.Repaired(
	repairedPageIndices = repairedPageIndices.toSet(),
	centerOrdinal = centerOrdinal,
	rasterEpoch = rasterEpoch,
	diagnosticOperation = diagnosticOperation
)

internal sealed interface ReaderPageRecoveredDeckBuildResult {
	data class Built(val generationId: Long) : ReaderPageRecoveredDeckBuildResult

	data class Failed(val reason: String) : ReaderPageRecoveredDeckBuildResult

	data object Stale : ReaderPageRecoveredDeckBuildResult
}

internal sealed interface ReaderPageRecoveredDeckSubmissionResult {
	data object Accepted : ReaderPageRecoveredDeckSubmissionResult
	data object AwaitingRendererCapacity : ReaderPageRecoveredDeckSubmissionResult
	data class Rejected(val reason: String) : ReaderPageRecoveredDeckSubmissionResult
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
	): ReaderPageRecoveredDeckSubmissionResult

	fun releaseUnsubmittedRecoveredDeck(generationId: Long)

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
		val rasterEpoch: Long,
		val diagnosticOperation: ReaderPageDiagnosticOperation? = null
	) : ReaderPageDeckRecoveryState

	data class WaitingForSubmissionCapacity(
		val generationId: Long,
		val repairedPageIndices: Set<Int>,
		val centerOrdinal: Int,
		val rasterEpoch: Long,
		val diagnosticOperation: ReaderPageDiagnosticOperation? = null
	) : ReaderPageDeckRecoveryState

	data class WaitingForPreparation(
		val generationId: Long,
		val role: ReaderDeckSubmissionRole,
		val diagnosticOperation: ReaderPageDiagnosticOperation? = null
	) : ReaderPageDeckRecoveryState

	data class Ready(
		val generationId: Long,
		val diagnosticOperation: ReaderPageDiagnosticOperation? = null
	) : ReaderPageDeckRecoveryState

	data class Failed(
		val reason: String,
		val diagnosticOperation: ReaderPageDiagnosticOperation? = null
	) : ReaderPageDeckRecoveryState
}

internal class ReaderPageDeckRecoveryCoordinator(
	private val host: ReaderPageDeckRecoveryHost,
	private val onStateChanged: (ReaderPageDeckRecoveryState) -> Unit = {},
	private val onRepairCancelled: (ReaderPageDiagnosticOperation) -> Unit = {},
	private val onStateObserverFailure: (Throwable) -> Unit = {}
) {
	private var nextRequestId = 0L
	private var retainedStateObserverFailure: Throwable? = null

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
			result.diagnosticOperation?.let(onRepairCancelled)
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
				rasterEpoch = result.rasterEpoch,
				diagnosticOperation = result.diagnosticOperation
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
		} catch (_: Throwable) {
			host.cancelRecoveredDeckBuild(requestId)
			val waiting = state as? ReaderPageDeckRecoveryState.WaitingForBuild
			if (waiting?.requestId == requestId) {
				transitionTo(
					ReaderPageDeckRecoveryState.Failed(
						"protected-window-build-failed",
						waiting.diagnosticOperation
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
			waiting.diagnosticOperation?.let(onRepairCancelled)
			transitionTo(ReaderPageDeckRecoveryState.Idle)
			return false
		}
		when (result) {
			is ReaderPageRecoveredDeckBuildResult.Failed -> {
				transitionTo(
					ReaderPageDeckRecoveryState.Failed(
						result.reason,
						waiting.diagnosticOperation
					)
				)
				return false
			}
			ReaderPageRecoveredDeckBuildResult.Stale -> {
				waiting.diagnosticOperation?.let(onRepairCancelled)
				transitionTo(ReaderPageDeckRecoveryState.Idle)
				return false
			}
			is ReaderPageRecoveredDeckBuildResult.Built -> Unit
		}
		val generationId = result.generationId
		return submitRecoveredGeneration(
			generationId = generationId,
			repairedPageIndices = waiting.repairedPageIndices,
			centerOrdinal = waiting.centerOrdinal,
			rasterEpoch = waiting.rasterEpoch,
			diagnosticOperation = waiting.diagnosticOperation
		)
	}

	fun onDeckSubmissionCapacityAvailable(): Boolean {
		val waiting = state as? ReaderPageDeckRecoveryState.WaitingForSubmissionCapacity
			?: return false
		if (!host.isCurrentRepairWindow(
				waiting.repairedPageIndices,
				waiting.centerOrdinal,
				waiting.rasterEpoch
			)
		) {
			host.releaseUnsubmittedRecoveredDeck(waiting.generationId)
			waiting.diagnosticOperation?.let(onRepairCancelled)
			transitionTo(ReaderPageDeckRecoveryState.Idle)
			return false
		}
		return submitRecoveredGeneration(
			generationId = waiting.generationId,
			repairedPageIndices = waiting.repairedPageIndices,
			centerOrdinal = waiting.centerOrdinal,
			rasterEpoch = waiting.rasterEpoch,
			diagnosticOperation = waiting.diagnosticOperation
		)
	}

	private fun submitRecoveredGeneration(
		generationId: Long,
		repairedPageIndices: Set<Int>,
		centerOrdinal: Int,
		rasterEpoch: Long,
		diagnosticOperation: ReaderPageDiagnosticOperation?
	): Boolean {
		val role = host.currentRecoveredDeckRole()
		transitionTo(
			ReaderPageDeckRecoveryState.WaitingForPreparation(
				generationId,
				role,
				diagnosticOperation
			)
		)
		val submission = try {
			host.submitRecoveredDeck(generationId, role)
		} catch (_: Throwable) {
			val owned = state as? ReaderPageDeckRecoveryState.WaitingForPreparation
			if (owned?.generationId == generationId && owned.role == role) {
				host.releaseUnsubmittedRecoveredDeck(generationId)
				transitionTo(
					ReaderPageDeckRecoveryState.Failed(
						"recovered-deck-submission-failed",
						owned.diagnosticOperation
					)
				)
			}
			return false
		}
		return when (submission) {
			ReaderPageRecoveredDeckSubmissionResult.Accepted ->
				state is ReaderPageDeckRecoveryState.WaitingForPreparation ||
					state is ReaderPageDeckRecoveryState.Ready
			ReaderPageRecoveredDeckSubmissionResult.AwaitingRendererCapacity -> {
				val owned = state as? ReaderPageDeckRecoveryState.WaitingForPreparation
				check(owned?.generationId == generationId && owned.role == role) {
					"Capacity-rejected recovered deck lost submission ownership"
				}
				transitionTo(
					ReaderPageDeckRecoveryState.WaitingForSubmissionCapacity(
						generationId = generationId,
						repairedPageIndices = repairedPageIndices.toSet(),
						centerOrdinal = centerOrdinal,
						rasterEpoch = rasterEpoch,
						diagnosticOperation = diagnosticOperation
					)
				)
				true
			}
			is ReaderPageRecoveredDeckSubmissionResult.Rejected -> {
				val owned = state as? ReaderPageDeckRecoveryState.WaitingForPreparation
				if (owned?.generationId == generationId && owned.role == role) {
					host.releaseUnsubmittedRecoveredDeck(generationId)
					transitionTo(
						ReaderPageDeckRecoveryState.Failed(
							"recovered-deck-rejected:${submission.reason}",
							owned.diagnosticOperation
						)
					)
				}
				false
			}
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
				"recovered-deck-rejected:${reason.name}",
				waiting.diagnosticOperation
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
		val diagnosticOperation =
			(state as? ReaderPageDeckRecoveryState.WaitingForPreparation)
				?.takeIf { waiting -> waiting.generationId == generationId }
				?.diagnosticOperation
		transitionTo(
			ReaderPageDeckRecoveryState.Failed(
				"recovered-deck-preparation-failed:$reason",
				diagnosticOperation
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
		transitionTo(
			ReaderPageDeckRecoveryState.Ready(
				generationId,
				waiting.diagnosticOperation
			)
		)
		return true
	}

	fun onDeckReleased(generationId: Long): Boolean {
		return when (val current = state) {
			is ReaderPageDeckRecoveryState.WaitingForPreparation -> {
				if (current.generationId != generationId) return false
				transitionTo(
					ReaderPageDeckRecoveryState.Failed(
						"recovered-deck-released-before-preparation",
						current.diagnosticOperation
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
			host.releaseUnsubmittedRecoveredDeck(result.generationId)
		}
	}

	private fun cancelOwnedState(previous: ReaderPageDeckRecoveryState) {
		when (previous) {
			is ReaderPageDeckRecoveryState.WaitingForBuild -> {
				host.cancelRecoveredDeckBuild(previous.requestId)
				previous.diagnosticOperation?.let(onRepairCancelled)
			}
			is ReaderPageDeckRecoveryState.WaitingForSubmissionCapacity -> {
				host.releaseUnsubmittedRecoveredDeck(previous.generationId)
				previous.diagnosticOperation?.let(onRepairCancelled)
			}
			is ReaderPageDeckRecoveryState.WaitingForPreparation -> {
				host.cancelSubmittedRecoveredDeck(
					previous.generationId,
					previous.role
				)
				previous.diagnosticOperation?.let(onRepairCancelled)
			}
			else -> Unit
		}
	}

	fun stateObserverFailure(): Throwable? = retainedStateObserverFailure

	private fun transitionTo(next: ReaderPageDeckRecoveryState) {
		if (state == next) return
		state = next
		try {
			onStateChanged(next)
		} catch (failure: Throwable) {
			val first = retainedStateObserverFailure
			if (first == null) retainedStateObserverFailure = failure
			else if (failure !== first) first.addSuppressed(failure)
			try {
				onStateObserverFailure(failure)
			} catch (reportingFailure: Throwable) {
				if (reportingFailure !== failure) failure.addSuppressed(reportingFailure)
			}
		}
	}
}
