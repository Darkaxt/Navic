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

	fun acknowledgedCommand(commandId: String): ReaderBridgeCommand? =
		pendingCommands.firstOrNull { it.dispatch.id == commandId }?.dispatch?.command

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
): ReaderWebCommandDispatchStep = commandsForReadyReaderRuntime(
	runtimeGeneration = runtimeGeneration,
	publicationKey = publicationKey,
	openCommand = openCommand,
	commands = listOfNotNull(command),
	commandKey = commandKey,
	rawTextProvenanceDescriptors = rawTextProvenanceDescriptors
)

fun ReaderWebCommandDispatchState.commandsForReadyReaderRuntime(
	runtimeGeneration: Int,
	publicationKey: String,
	openCommand: ReaderBridgeCommand.OpenPublication,
	commands: List<ReaderBridgeCommand>,
	commandKey: Long,
	rawTextProvenanceDescriptors: List<ReaderRawTextProvenanceDescriptor> = emptyList()
): ReaderWebCommandDispatchStep {
	val publicationChanged = this.publicationKey != publicationKey
	val generationChanged = this.runtimeGeneration != runtimeGeneration
	val explicitProvenanceInstalls = commands
		.filterIsInstance<ReaderBridgeCommand.InstallRawTextProvenance>()
		.map(ReaderBridgeCommand.InstallRawTextProvenance::descriptor)
	val commandKeys = commands.indices.map { index ->
		commandKey - (commands.lastIndex - index)
	}
	val currentCommandIds = commandKeys.map { key -> "reader-command-$publicationSequence-$key" }
	val currentCommandWasPending = currentCommandIds.any { commandId ->
		pendingCommands.any { it.dispatch.id == commandId }
	}
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

	val shouldQueueCurrentCommands = commands.isNotEmpty() && when {
		publicationChanged -> true
		generationChanged -> currentCommandWasPending || lastCommandKey != commandKey
		else -> nextState.lastCommandKey != commandKey
	}

	rawTextProvenanceDescriptors.forEach { descriptor ->
		if (nextState.rawTextProvenanceById[descriptor.id] != descriptor) {
			val nextProvenanceSequence = nextState.rawTextProvenanceSequence + 1L
			val hasEqualExplicitInstall = descriptor in explicitProvenanceInstalls
			val durableInstall = ReaderWebPendingCommand(
				dispatch = ReaderBridgeDispatchCommand(
					id = "reader-provenance-${nextState.publicationSequence}-$nextProvenanceSequence",
					command = ReaderBridgeCommand.InstallRawTextProvenance(descriptor)
				)
			).takeUnless { hasEqualExplicitInstall && shouldQueueCurrentCommands }
			nextState = nextState.copy(
				rawTextProvenanceById =
					nextState.rawTextProvenanceById + (descriptor.id to descriptor),
				rawTextProvenanceSequence = nextProvenanceSequence,
				pendingCommands = nextState.pendingCommands + listOfNotNull(durableInstall)
			)
		}
	}

	if (shouldQueueCurrentCommands) {
		nextState = nextState.copy(
			lastCommandKey = commandKey,
			pendingCommands = nextState.pendingCommands + commands.zip(commandKeys).map { (command, key) ->
				ReaderWebPendingCommand(
					dispatch = ReaderBridgeDispatchCommand(
						id = "reader-command-${nextState.publicationSequence}-$key",
						command = command
					)
				)
			}
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
