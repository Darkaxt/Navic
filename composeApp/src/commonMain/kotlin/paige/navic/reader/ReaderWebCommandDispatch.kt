package paige.navic.reader

data class ReaderWebPendingCommand(
	val dispatch: ReaderBridgeDispatchCommand,
	val lastDispatchedGeneration: Int? = null
)

data class ReaderWebCommandDispatchState(
	val publicationKey: String? = null,
	val publicationSequence: Long = 0L,
	val runtimeGeneration: Int? = null,
	val foliateSessionId: String? = null,
	val lastCommandKey: Long? = null,
	val lastKnownLocator: ReaderLocator? = null,
	val rawTextProvenanceById: Map<String, ReaderRawTextProvenanceDescriptor> = emptyMap(),
	val rawTextProvenanceSequence: Long = 0L,
	val pendingCommands: List<ReaderWebPendingCommand> = emptyList()
) {
	fun observeLocator(locator: ReaderLocator): ReaderWebCommandDispatchState =
		copy(lastKnownLocator = locator)

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
	commandKey: Long,
	rawTextProvenanceDescriptors: List<ReaderRawTextProvenanceDescriptor> = emptyList()
): ReaderWebCommandDispatchStep {
	val publicationChanged = this.publicationKey != publicationKey
	val generationChanged = this.runtimeGeneration != runtimeGeneration
	val currentCommandId = "reader-command-$publicationSequence-$commandKey"
	val currentCommandWasPending = command != null &&
		pendingCommands.any { it.dispatch.id == currentCommandId }
	var nextState = when {
		publicationChanged -> ReaderWebCommandDispatchState(
			publicationKey = publicationKey,
			publicationSequence = publicationSequence + 1L,
			runtimeGeneration = runtimeGeneration,
			lastKnownLocator = openCommand.startLocator
		)
		generationChanged -> copy(
			runtimeGeneration = runtimeGeneration,
			rawTextProvenanceById = emptyMap(),
			pendingCommands = emptyList()
		)
		else -> this
	}
	val runtimeSessionId = if (publicationChanged || generationChanged) {
		"foliate-${nextState.publicationSequence}-$runtimeGeneration"
	} else {
		checkNotNull(nextState.foliateSessionId)
	}
	nextState = nextState.copy(foliateSessionId = runtimeSessionId)

	if (publicationChanged || generationChanged) {
		nextState = nextState.copy(
			pendingCommands = listOf(
				ReaderWebPendingCommand(
					dispatch = ReaderBridgeDispatchCommand(
						id = "reader-open-${nextState.publicationSequence}",
						command = openCommand.copy(
							foliateSessionId = runtimeSessionId,
							startLocator = nextState.lastKnownLocator ?: openCommand.startLocator
						)
					)
				)
			)
		)
	}

	rawTextProvenanceDescriptors.forEach { descriptor ->
		if (nextState.rawTextProvenanceById[descriptor.id] != descriptor) {
			val nextProvenanceSequence = nextState.rawTextProvenanceSequence + 1L
			nextState = nextState.copy(
				rawTextProvenanceById =
					nextState.rawTextProvenanceById + (descriptor.id to descriptor),
				rawTextProvenanceSequence = nextProvenanceSequence,
				pendingCommands = nextState.pendingCommands + ReaderWebPendingCommand(
					dispatch = ReaderBridgeDispatchCommand(
						id = "reader-provenance-${nextState.publicationSequence}-$nextProvenanceSequence",
						command = ReaderBridgeCommand.InstallRawTextProvenance(descriptor)
					)
				)
			)
		}
	}

	val shouldQueueCurrentCommand = command != null && when {
		publicationChanged -> true
		generationChanged -> currentCommandWasPending || lastCommandKey != commandKey
		else -> nextState.lastCommandKey != commandKey
	}
	if (shouldQueueCurrentCommand) {
		nextState = nextState.copy(
			lastCommandKey = commandKey,
			pendingCommands = nextState.pendingCommands + ReaderWebPendingCommand(
				dispatch = ReaderBridgeDispatchCommand(
					id = "reader-command-${nextState.publicationSequence}-$commandKey",
					command = checkNotNull(command)
				)
			)
		)
	}

	val commands = nextState.pendingCommands
		.firstOrNull()
		?.takeIf { it.lastDispatchedGeneration != runtimeGeneration }
		?.let { listOf(it.dispatch) }
		.orEmpty()
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
