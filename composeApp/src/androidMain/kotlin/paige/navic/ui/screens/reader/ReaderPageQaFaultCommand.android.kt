package paige.navic.ui.screens.reader

sealed interface ReaderPageQaFaultCommand {
	val requestId: String

	data class Enqueue(
		override val requestId: String,
		val fault: ReaderPageQaFault
	) : ReaderPageQaFaultCommand

	data class ReleasePublication(override val requestId: String) :
		ReaderPageQaFaultCommand

	data class ReleaseRelocation(override val requestId: String) :
		ReaderPageQaFaultCommand

	data class ReleaseVisualState(override val requestId: String) :
		ReaderPageQaFaultCommand

	data class Clear(override val requestId: String) : ReaderPageQaFaultCommand

	data class Rejected(
		override val requestId: String,
		val reason: Rejection
	) : ReaderPageQaFaultCommand

	enum class Rejection {
		InvalidAction,
		InvalidRequestId,
		InvalidCommand,
		InvalidFault
	}
}

object ReaderPageQaFaultCommandDecoder {
	const val Action = "darkaxt.navic.readerdev.READER_QA_FAULT"

	fun acceptsAction(action: String?): Boolean = action == Action

	fun decode(
		requestId: String?,
		command: String?,
		faultName: String?
	): ReaderPageQaFaultCommand {
		val safeId = requestId?.takeIf(::isReaderPageQaRequestId)
			?: return ReaderPageQaFaultCommand.Rejected(
				"invalid",
				ReaderPageQaFaultCommand.Rejection.InvalidRequestId
			)
		return when (command) {
			"enqueue" -> ReaderPageQaFault.entries
				.firstOrNull { it.name == faultName }
				?.let { ReaderPageQaFaultCommand.Enqueue(safeId, it) }
				?: ReaderPageQaFaultCommand.Rejected(
					safeId,
					ReaderPageQaFaultCommand.Rejection.InvalidFault
				)
			"release-publication" ->
				ReaderPageQaFaultCommand.ReleasePublication(safeId)
			"release-relocation" ->
				ReaderPageQaFaultCommand.ReleaseRelocation(safeId)
			"release-visual-state" ->
				ReaderPageQaFaultCommand.ReleaseVisualState(safeId)
			"clear" -> ReaderPageQaFaultCommand.Clear(safeId)
			else -> ReaderPageQaFaultCommand.Rejected(
				safeId,
				ReaderPageQaFaultCommand.Rejection.InvalidCommand
			)
		}
	}
}
