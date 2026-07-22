package paige.navic.ui.screens.reader

import karacken.curl.PageChange
import paige.navic.reader.ReaderPageGestureTerminalOutcome

internal fun interface ReaderPageTapTurnPort {
	fun start(
		gestureId: Long,
		pageChange: PageChange,
		onTerminal: (
			ReaderPageGestureTerminalOutcome,
			ReaderPageGestureTerminalDetail
		) -> Boolean
	): ReaderPageTurnStartResult
}

internal class ReaderPageTapTurnControllerFacade(
	private val port: ReaderPageTapTurnPort,
	private val publishTerminal: (
		Long,
		ReaderPageGestureTerminalOutcome,
		ReaderPageGestureTerminalDetail
	) -> Boolean
) {
	private var settlingGestureId: Long? = null

	fun turn(
		gestureId: Long,
		pageChange: PageChange
	): ReaderPageTurnStartResult {
		if (settlingGestureId != null) {
			val detail = ReaderPageGestureTerminalDetail.TapTurnUnavailable(pageChange)
			publishTerminal(
				gestureId,
				ReaderPageGestureTerminalOutcome.RejectedSettling,
				detail
			)
			return ReaderPageTurnStartResult.TerminalPublished(
				outcome = ReaderPageGestureTerminalOutcome.RejectedSettling,
				detail = detail
			)
		}

		settlingGestureId = gestureId
		var callbackObserved = false
		val result = try {
			port.start(gestureId, pageChange) { outcome, detail ->
				callbackObserved = true
				if (settlingGestureId == gestureId) settlingGestureId = null
				publishTerminal(gestureId, outcome, detail)
			}
		} catch (failure: Throwable) {
			if (settlingGestureId == gestureId) settlingGestureId = null
			throw failure
		}
		when (result) {
			ReaderPageTurnStartResult.Settling -> check(!callbackObserved) {
				"Settling tap turn published a synchronous terminal"
			}
			is ReaderPageTurnStartResult.TerminalPublished -> check(callbackObserved) {
				"TerminalPublished returned before callback publication"
			}
		}
		return result
	}
}
