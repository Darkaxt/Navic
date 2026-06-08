package paige.navic.reader

data class ReaderWebCommandDispatchState(
	val publicationKey: String? = null,
	val lastCommandKey: Long? = null
)

data class ReaderWebCommandDispatchStep(
	val state: ReaderWebCommandDispatchState,
	val commands: List<ReaderBridgeCommand>
)

fun ReaderWebCommandDispatchState.commandsForReadyReaderRuntime(
	publicationKey: String,
	openCommand: ReaderBridgeCommand.OpenPublication,
	command: ReaderBridgeCommand?,
	commandKey: Long
): ReaderWebCommandDispatchStep {
	val commands = mutableListOf<ReaderBridgeCommand>()
	var nextState = this
	if (nextState.publicationKey != publicationKey) {
		commands += openCommand
		nextState = ReaderWebCommandDispatchState(publicationKey = publicationKey)
	}
	if (command != null && nextState.lastCommandKey != commandKey) {
		commands += command
		nextState = nextState.copy(lastCommandKey = commandKey)
	}
	return ReaderWebCommandDispatchStep(
		state = nextState,
		commands = commands
	)
}
