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

	fun isCurrent(): Boolean =
		!terminal && ownership.isCurrent(claim, generation)

	fun commit(snapshotKey: Int): Boolean {
		if (terminal) return false
		terminal = true
		val current = ownership.isCurrent(claim, generation)
		try {
			if (current) onSnapshotCommitted(snapshotKey)
		} finally {
			ownership.releaseLive(claim)
		}
		return current
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
