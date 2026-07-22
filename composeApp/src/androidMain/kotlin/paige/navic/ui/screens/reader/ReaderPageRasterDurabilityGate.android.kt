package paige.navic.ui.screens.reader

internal sealed interface ReaderPageRasterDurabilityDecision {
	data class Continue(
		val completed: Int,
		val required: Int
	) : ReaderPageRasterDurabilityDecision

	data class Failed(
		val pageIndex: Int
	) : ReaderPageRasterDurabilityDecision

	data object Ready : ReaderPageRasterDurabilityDecision
}

internal class ReaderPageRasterDurabilityGate(
	requiredPageIndices: Set<Int>
) {
	private val required = requiredPageIndices.toSet()
	private val durable = linkedSetOf<Int>()

	init {
		require(required.isNotEmpty())
	}

	fun record(
		pageIndex: Int,
		persisted: Boolean
	): ReaderPageRasterDurabilityDecision {
		require(pageIndex in required) { "Unexpected page index $pageIndex" }
		if (!persisted) {
			return ReaderPageRasterDurabilityDecision.Failed(pageIndex)
		}
		durable += pageIndex
		return if (durable == required) {
			ReaderPageRasterDurabilityDecision.Ready
		} else {
			ReaderPageRasterDurabilityDecision.Continue(
				completed = durable.size,
				required = required.size
			)
		}
	}

	fun retryPageIndices(): Set<Int> = required - durable
}
