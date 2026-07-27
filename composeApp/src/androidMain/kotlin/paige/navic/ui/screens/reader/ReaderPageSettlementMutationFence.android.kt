package paige.navic.ui.screens.reader

internal class ReaderPageSettlementMutationFence {
	private data class SettlementSource(
		val gestureId: Long,
		val sourceGenerationId: Long
	)

	private var settlementSource: SettlementSource? = null
	private var deferredRefresh = false

	val hasUnreconciledSettlement: Boolean
		get() = settlementSource != null

	fun onSettlementStarted(gestureId: Long, sourceGenerationId: Long) {
		check(settlementSource == null) { "A settlement is already awaiting reconciliation" }
		settlementSource = SettlementSource(gestureId, sourceGenerationId)
	}

	fun blocksExternalDeckMutation(activeGestureId: Long?): Boolean =
		activeGestureId != null || hasUnreconciledSettlement

	fun deferRefreshIfBlocked(activeGestureId: Long?): Boolean {
		if (!blocksExternalDeckMutation(activeGestureId)) return false
		deferredRefresh = true
		return true
	}

	fun takeDeferredRefreshIfUnblocked(activeGestureId: Long?): Boolean {
		if (blocksExternalDeckMutation(activeGestureId) || !deferredRefresh) return false
		deferredRefresh = false
		return true
	}

	fun onSettlementReconciled(
		gestureId: Long,
		sourceGenerationId: Long,
		activeGestureId: Long?
	): Boolean {
		val current = checkNotNull(settlementSource) {
			"Settlement reconciliation has no controller-owned source"
		}
		check(
			current.gestureId == gestureId &&
				current.sourceGenerationId == sourceGenerationId
		) { "Settlement reconciliation does not match its controller-owned source" }
		settlementSource = null
		return takeDeferredRefreshIfUnblocked(activeGestureId)
	}
}
