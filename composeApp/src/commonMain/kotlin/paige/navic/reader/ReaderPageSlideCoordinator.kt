package paige.navic.reader

sealed interface ReaderPageSlideCoordinatorEffect {
	data class SettleExact(val pageIndex: Int) : ReaderPageSlideCoordinatorEffect
	data object RemoveFinalShield : ReaderPageSlideCoordinatorEffect
}

class ReaderPageSlideCoordinator(initialPageIndex: Int) {
	var generation: Long = 1L
		private set
	var visualPageIndex: Int = initialPageIndex.requirePageIndex()
		private set
	var settledPageIndex: Int = visualPageIndex
		private set
	var activeSettlementTarget: Int? = null
		private set
	var pendingTargetPageIndex: Int? = null
		private set

	fun visualCommitted(pageIndex: Int): List<ReaderPageSlideCoordinatorEffect> {
		val target = pageIndex.requirePageIndex()
		visualPageIndex = target
		if (activeSettlementTarget != null) {
			pendingTargetPageIndex = target.takeUnless { it == activeSettlementTarget }
			return emptyList()
		}
		if (settledPageIndex == target) return emptyList()
		activeSettlementTarget = target
		pendingTargetPageIndex = null
		return listOf(ReaderPageSlideCoordinatorEffect.SettleExact(target))
	}

	fun settlementReported(
		reportedGeneration: Long,
		pageIndex: Int,
		renderable: Boolean
	): List<ReaderPageSlideCoordinatorEffect> {
		if (reportedGeneration != generation || pageIndex != activeSettlementTarget) return emptyList()
		settledPageIndex = pageIndex
		activeSettlementTarget = null

		if (settledPageIndex != visualPageIndex) {
			val latestTarget = visualPageIndex
			pendingTargetPageIndex = null
			activeSettlementTarget = latestTarget
			return listOf(ReaderPageSlideCoordinatorEffect.SettleExact(latestTarget))
		}

		pendingTargetPageIndex = null
		return if (renderable) {
			listOf(ReaderPageSlideCoordinatorEffect.RemoveFinalShield)
		} else {
			emptyList()
		}
	}

	fun frameBecameRenderable(
		reportedGeneration: Long,
		pageIndex: Int
	): List<ReaderPageSlideCoordinatorEffect> {
		if (
			reportedGeneration != generation ||
			activeSettlementTarget != null ||
			pageIndex != visualPageIndex ||
			settledPageIndex != visualPageIndex
		) return emptyList()
		return listOf(ReaderPageSlideCoordinatorEffect.RemoveFinalShield)
	}

	fun invalidate(newPageIndex: Int): Long {
		generation += 1
		visualPageIndex = newPageIndex.requirePageIndex()
		settledPageIndex = visualPageIndex
		activeSettlementTarget = null
		pendingTargetPageIndex = null
		return generation
	}
}

private fun Int.requirePageIndex(): Int {
	require(this >= 0) { "Page index must be non-negative" }
	return this
}
