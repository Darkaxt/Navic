package paige.navic.ui.screens.reader

internal sealed interface ReaderSettingsWebViewMutationReadiness {
	data class Ready(
		val mutation: ReaderSettingsWebViewMutation
	) : ReaderSettingsWebViewMutationReadiness

	data class Rejected(
		val readiness: ReaderForegroundWebViewLiveReadiness
	) : ReaderSettingsWebViewMutationReadiness
}

internal fun interface ReaderSettingsWebViewMutationHost {
	fun acquireSettingsMutation(
		requestId: Long,
		onReadiness: (ReaderSettingsWebViewMutationReadiness) -> Unit
	)
}

internal class ReaderSettingsWebViewMutation internal constructor(
	private val ownership: ReaderForegroundWebViewOwnership,
	private val claim: ReaderForegroundWebViewLiveClaim,
	private val generation: ReaderForegroundWebViewMutationGeneration,
	private val onSnapshotCommitted: (Int) -> Unit
) {
	private var terminal = false
	private var snapshotKey: Int? = null
	private var publicationPhase = 0
	private var onCommitted: () -> Unit = {}
	private var dispatchNext: () -> Unit = {}

	fun isCurrent(): Boolean = !terminal && ownership.isCurrent(claim, generation)

	fun invokeMutation(invoke: () -> Unit): Boolean =
		!terminal && ownership.invokeLiveMutation(claim, generation, invoke)

	fun <T> invokeMutationWithResult(invoke: ((T) -> Unit) -> Unit, onResult: (T) -> Unit): Boolean =
		!terminal && ownership.invokeLiveMutationWithResult(claim, generation, invoke, onResult)

	fun deferPhase(invoke: () -> Unit): Boolean =
		!terminal && ownership.deferLiveContinuation(claim, generation, invoke)

	fun commit(snapshotKey: Int): Boolean = completePresentation(snapshotKey, {}, {})

	fun completePresentation(snapshotKey: Int, onCommitted: () -> Unit, dispatchNext: () -> Unit): Boolean {
		if (terminal) return false
		if (this.snapshotKey == null) {
			this.snapshotKey = snapshotKey
			this.onCommitted = onCommitted
			this.dispatchNext = dispatchNext
		} else if (this.snapshotKey != snapshotKey) return false
		return publishNextPhase()
	}

	private fun publishNextPhase(): Boolean {
		if (terminal) return false
		try {
			val published = ownership.invokeLivePublication(claim, generation) {
				// Consume this recipient only at invocation, never at reservation/checkpoint time.
				when (publicationPhase++) {
					0 -> onSnapshotCommitted(checkNotNull(snapshotKey))
					1 -> onCommitted()
					2 -> {
						terminal = true
						ownership.releaseLive(claim, dispatchNext)
					}
				}
			}
			if (!published) {
				deferPhase { publishNextPhase() }
				return false
			}
		} catch (failure: Throwable) {
			// The invoked recipient is consumed. A refreeze parks only the remaining phases;
			// without a freeze, finish safe remaining recipients now rather than await a nonexistent restore.
			if (ownership.isFrozenForTransitionActivation()) deferPhase { publishNextPhase() }
			else runCatching { publishNextPhase() }
			throw failure
		}
		return if (terminal) true else publishNextPhase()
	}

	fun cancel(): Boolean {
		if (terminal) return false
		terminal = true
		return ownership.releaseLive(claim)
	}
}

internal class ReaderSettingsWebViewMutationCoordinator(
	private val ownership: ReaderForegroundWebViewOwnership,
	private val onSnapshotCommitted: (Int) -> Unit
) : ReaderSettingsWebViewMutationHost {
	override fun acquireSettingsMutation(
		requestId: Long,
		onReadiness: (ReaderSettingsWebViewMutationReadiness) -> Unit
	) {
		require(requestId in 1L..ReaderPageTurnPresentationMaximumSafeInteger)
		if (ownership.snapshot().closed) {
			onReadiness.rejected(ReaderForegroundWebViewLiveReadiness.Invalidated)
			return
		}
		val claim = ownership.acquireExclusiveLive(requestId)
		ownership.whenLiveReady(claim) { readiness ->
			if (readiness != ReaderForegroundWebViewLiveReadiness.Ready) {
				ownership.releaseLive(claim)
				onReadiness.rejected(readiness)
				return@whenLiveReady
			}
			val generation = ownership.beginLiveMutation(claim)
			if (generation == null) {
				ownership.releaseLive(claim)
				onReadiness.rejected(
					ReaderForegroundWebViewLiveReadiness.Invalidated
				)
				return@whenLiveReady
			}
			onReadiness(
				ReaderSettingsWebViewMutationReadiness.Ready(
					ReaderSettingsWebViewMutation(
						ownership = ownership,
						claim = claim,
						generation = generation,
						onSnapshotCommitted = onSnapshotCommitted
					)
				)
			)
		}
	}

	private fun ((ReaderSettingsWebViewMutationReadiness) -> Unit).rejected(
		readiness: ReaderForegroundWebViewLiveReadiness
	) {
		invoke(ReaderSettingsWebViewMutationReadiness.Rejected(readiness))
	}
}
