package paige.navic.reader

data class ReaderWebPendingCommand(
	val dispatch: ReaderBridgeDispatchCommand,
	val lastDispatchedGeneration: Int? = null
)

data class ReaderWebCommandDispatchState(
	val publicationKey: String? = null,
	val publicationSequence: Long = 0L,
	val runtimeGeneration: Int? = null,
	val lastCommandKey: Long? = null,
	val pendingCommands: List<ReaderWebPendingCommand> = emptyList()
) {
	fun acknowledge(commandId: String): ReaderWebCommandDispatchState {
		val retainedCommands = pendingCommands.filterNot { it.dispatch.id == commandId }
		return if (retainedCommands.size == pendingCommands.size) {
			this
		} else {
			copy(pendingCommands = retainedCommands)
		}
	}
}

data class ReaderWebCommandDispatchStep(
	val state: ReaderWebCommandDispatchState,
	val commands: List<ReaderBridgeDispatchCommand>
)

fun shouldDispatchReaderCommandsToWebRuntime(
	runtimeReady: Boolean,
	currentUrl: String?,
	entrypointUrl: String
): Boolean =
	runtimeReady && currentUrl == entrypointUrl

fun ReaderWebCommandDispatchState.commandsForReadyReaderRuntime(
	runtimeGeneration: Int,
	publicationKey: String,
	openCommand: ReaderBridgeCommand.OpenPublication,
	command: ReaderBridgeCommand?,
	commandKey: Long
): ReaderWebCommandDispatchStep {
	val publicationChanged = this.publicationKey != publicationKey
	val generationChanged = this.runtimeGeneration != runtimeGeneration
	var nextState = when {
		publicationChanged -> ReaderWebCommandDispatchState(
			publicationKey = publicationKey,
			publicationSequence = publicationSequence + 1L,
			runtimeGeneration = runtimeGeneration
		)
		generationChanged -> copy(
			runtimeGeneration = runtimeGeneration,
			lastCommandKey = null,
			pendingCommands = emptyList()
		)
		else -> this
	}

	if (publicationChanged || generationChanged) {
		nextState = nextState.copy(
			pendingCommands = listOf(
				ReaderWebPendingCommand(
					dispatch = ReaderBridgeDispatchCommand(
						id = "reader-open-${nextState.publicationSequence}",
						command = openCommand
					)
				)
			)
		)
	}

	if (command != null && nextState.lastCommandKey != commandKey) {
		nextState = nextState.copy(
			lastCommandKey = commandKey,
			pendingCommands = nextState.pendingCommands + ReaderWebPendingCommand(
				dispatch = ReaderBridgeDispatchCommand(
					id = "reader-command-${nextState.publicationSequence}-$commandKey",
					command = command
				)
			)
		)
	}

	val commands = nextState.pendingCommands
		.filter { it.lastDispatchedGeneration != runtimeGeneration }
		.map { it.dispatch }
	if (commands.isNotEmpty()) {
		val dispatchedIds = commands.mapTo(mutableSetOf()) { it.id }
		nextState = nextState.copy(
			pendingCommands = nextState.pendingCommands.map { pending ->
				if (pending.dispatch.id in dispatchedIds) {
					pending.copy(lastDispatchedGeneration = runtimeGeneration)
				} else {
					pending
				}
			}
		)
	}

	return ReaderWebCommandDispatchStep(
		state = nextState,
		commands = commands
	)
}
