package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageTurnDirection

internal data class ReaderPagePersistentRefillFence(
	val committedTurnVersion: Long,
	val destinationOrdinal: Int,
	val protectedWindowVersion: Long,
	val protectedWindow: List<Int>
)

internal class ReaderPagePersistentRefillCoordinator(
	private val protectedWindowForCenter: (Int) -> List<Int>,
	private val publishProtectedWindow: (List<Int>) -> Long,
	private val isDecoded: (Int) -> Boolean,
	private val hydratePersistent: suspend (
		Int,
		ReaderPagePersistentRefillFence,
		(ReaderPagePersistentRefillFence) -> Boolean
	) -> Boolean,
	private val requestRepair: (Int) -> Unit
) {
	suspend fun onTurnCommitted(
		direction: ReaderPageTurnDirection,
		destinationOrdinal: Int,
		committedTurnVersion: Long,
		isTurnStillCurrent: () -> Boolean,
		isStillCurrent: (ReaderPagePersistentRefillFence) -> Boolean
	) {
		if (!isTurnStillCurrent()) return
		val protectedWindow = protectedWindowForCenter(destinationOrdinal).toList()
		val fence = ReaderPagePersistentRefillFence(
			committedTurnVersion = committedTurnVersion,
			destinationOrdinal = destinationOrdinal,
			protectedWindowVersion = publishProtectedWindow(protectedWindow),
			protectedWindow = protectedWindow
		)
		if (!isStillCurrent(fence)) return
		val farEdge = when (direction) {
			ReaderPageTurnDirection.Next -> protectedWindow.lastOrNull()
			ReaderPageTurnDirection.Previous -> protectedWindow.firstOrNull()
		} ?: return
		if (isDecoded(farEdge)) return
		val hydrated = hydratePersistent(farEdge, fence, isStillCurrent)
		if (!isStillCurrent(fence)) return
		if (!hydrated) requestRepair(farEdge)
	}
}
