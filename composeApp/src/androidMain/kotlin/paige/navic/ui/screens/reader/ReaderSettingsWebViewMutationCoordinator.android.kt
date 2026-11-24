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
	private val onSnapshotCommitted: (snapshotKey: Int, layoutKey: Int) -> Unit,
	private val onFinished: () -> Unit = {}
) {
	private var terminal = false

	fun isCurrent(): Boolean =
		!terminal && ownership.isCurrent(claim, generation)

	fun commit(snapshotKey: Int, layoutKey: Int): Boolean {
		if (terminal) return false
		terminal = true
		val current = ownership.isCurrent(claim, generation)
		try {
			if (current) onSnapshotCommitted(snapshotKey, layoutKey)
		} finally {
			ownership.releaseLive(claim)
			onFinished()
		}
		return current
	}

	fun cancel(): Boolean {
		if (terminal) return false
		terminal = true
		return try {
			ownership.releaseLive(claim)
		} finally {
			onFinished()
		}
	}
}

/**
 * [onMutationStarted] runs when a settings mutation is handed out and [onMutationFinished] once it
 * commits or is cancelled, so the host can treat the whole relayout as one operation.
 */
internal class ReaderSettingsWebViewMutationCoordinator(
	private val ownership: ReaderForegroundWebViewOwnership,
	private val onSnapshotCommitted: (snapshotKey: Int, layoutKey: Int) -> Unit,
	private val onMutationStarted: () -> Unit = {},
	private val onMutationFinished: () -> Unit = {}
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
			onMutationStarted()
			onReadiness(
				ReaderSettingsWebViewMutationReadiness.Ready(
					ReaderSettingsWebViewMutation(
						ownership = ownership,
						claim = claim,
						generation = generation,
						onSnapshotCommitted = onSnapshotCommitted,
						onFinished = onMutationFinished
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
