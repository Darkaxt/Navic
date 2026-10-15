package paige.navic.reader

sealed interface ReaderCurrentDestinationQueryOutcome {
	class Resolved internal constructor(
		val event: ReaderBridgeEvent.CurrentDestinationResolved,
		internal val carrier: ReaderCurrentDestinationContinuation?
	) : ReaderCurrentDestinationQueryOutcome {
		constructor(event: ReaderBridgeEvent.CurrentDestinationResolved) : this(event, null)
		override fun toString() = "ReaderCurrentDestinationQueryOutcome.Resolved(<redacted>)"
	}
	class Failed(val reason: ReaderCurrentDestinationQueryFailure) : ReaderCurrentDestinationQueryOutcome
}

enum class ReaderCurrentDestinationQueryFailure {
	QueryCommandFailed, RuntimeRetired, PublicationReplaced, Closed, SubmissionRejected, SubmissionThrew
}

fun interface ReaderCurrentDestinationContinuation {
	fun complete(outcome: ReaderCurrentDestinationQueryOutcome)
}

/** Callback custody lives on the original pending command, not a second request registry. */
fun interface ReaderInstalledPageTurnContinuation {
	fun complete(event: ReaderBridgeEvent.LocationChanged?)
}

internal class ReaderInstalledPageTurnConsumptionStep(
	val state: ReaderWebCommandDispatchState,
	val continuation: ReaderInstalledPageTurnContinuation? = null,
	val event: ReaderBridgeEvent.LocationChanged? = null
)

data class ReaderWebPendingCommand(
	val dispatch: ReaderBridgeDispatchCommand,
	val lastDispatchedGeneration: Int? = null,
	val currentDestinationContinuation: ReaderCurrentDestinationContinuation? = null,
	// Only an authentic initial query owns pre-Ready opening custody in this same ledger.
	val requiresDispatchedOpening: Boolean = false,
	val installedPageTurnContinuation: ReaderInstalledPageTurnContinuation? = null
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
		// Query ACK is transport-only. An initial query also retains the actual unstarted
		// opening: an unsolicited opening ACK cannot skip real Foliate initialization.
		val opening = pendingCommands.firstOrNull { it.dispatch.id == commandId }
		if (opening?.dispatch?.command is ReaderBridgeCommand.OpenPublication &&
			opening.lastDispatchedGeneration != runtimeGeneration &&
			pendingCommands.any { it.requiresDispatchedOpening && it.currentDestinationContinuation != null }
		) return this
		// Ordinary ACKs outside this initial custody are unchanged.
		val retainedCommands = pendingCommands.filterNot {
			it.dispatch.id == commandId &&
				it.dispatch.command !is ReaderBridgeCommand.ResolveCurrentDestination &&
				it.dispatch.command !is ReaderBridgeCommand.GoToVisualPage
		}
		return if (retainedCommands.size == pendingCommands.size) {
			this
		} else {
			copy(pendingCommands = retainedCommands)
		}
	}

	internal fun enqueueInstalledPageTurn(
		dispatch: ReaderBridgeDispatchCommand,
		continuation: ReaderInstalledPageTurnContinuation
	): ReaderWebCommandDispatchState? {
		val turn = dispatch.command as? ReaderBridgeCommand.GoToVisualPage ?: return null
		if (publicationKey == null || runtimeGeneration == null || turn.settleSessionId != foliateSessionId ||
			pendingCommands.any { it.dispatch.id == dispatch.id || it.installedPageTurnContinuation === continuation }) return null
		return copy(pendingCommands = pendingCommands + ReaderWebPendingCommand(
			dispatch, installedPageTurnContinuation = continuation))
	}

	internal fun consumeInstalledPageTurnResult(event: ReaderBridgeEvent, generation: Int): ReaderInstalledPageTurnConsumptionStep {
		val rejected = ReaderInstalledPageTurnConsumptionStep(this)
		val pending = pendingCommands.firstOrNull() ?: return rejected
		val turn = pending.dispatch.command as? ReaderBridgeCommand.GoToVisualPage ?: return rejected
		if (runtimeGeneration != generation || pending.lastDispatchedGeneration != generation ||
			turn.settleSessionId != foliateSessionId) return rejected
		val location = event as? ReaderBridgeEvent.LocationChanged
		if (location != null) {
			if (location.commandId != pending.dispatch.id || location.foliateSessionId != turn.settleSessionId ||
				location.pageTurnSettleToken != turn.settleToken || location.pageTurnSettleSessionId != turn.settleSessionId ||
				location.pageTurnSettleRasterGeneration != turn.settleRasterGeneration ||
				location.pageTurnSettleTextureGeneration != turn.settleTextureGeneration ||
				location.locator.pageIndex != turn.pageIndex ||
				location.destinationCommitIdentity?.foliateSessionId != turn.settleSessionId ||
				location.destinationCommitIdentity.commitSequence <= turn.sourceDestinationCommitSequence) return rejected
		} else if ((event as? ReaderBridgeEvent.CommandFailed)?.commandId != pending.dispatch.id) return rejected
		return ReaderInstalledPageTurnConsumptionStep(copy(pendingCommands = pendingCommands.drop(1),
			lastKnownLocator = location?.locator ?: lastKnownLocator), pending.installedPageTurnContinuation, location)
	}

	internal fun retireInstalledPageTurnExact(continuation: ReaderInstalledPageTurnContinuation): ReaderWebCommandDispatchState {
		val retained = pendingCommands.filterNot { it.installedPageTurnContinuation === continuation }
		return if (retained.size == pendingCommands.size) this else copy(pendingCommands = retained)
	}

	internal fun closeInstalledPageTurns(): ReaderWebCommandDispatchState {
		val retained = pendingCommands.filterNot { it.dispatch.command is ReaderBridgeCommand.GoToVisualPage }
		return if (retained.size == pendingCommands.size) this else copy(pendingCommands = retained)
	}

	// Only the actually dispatched head owns query custody. Consume it before a caller
	// delivers the typed result; ordinary location observation is a separate boundary.
	internal fun consumeCurrentDestinationResult(
		event: ReaderBridgeEvent,
		runtimeGeneration: Int
	): ReaderCurrentDestinationConsumptionStep {
		val rejected = ReaderCurrentDestinationConsumptionStep(this, null)
		val commandId = when (event) {
			is ReaderBridgeEvent.CurrentDestinationResolved -> event.commandId
			is ReaderBridgeEvent.CommandFailed -> event.commandId
			else -> return rejected
		}
		val pending = pendingCommands.firstOrNull() ?: return rejected
		val query = pending.dispatch.command as? ReaderBridgeCommand.ResolveCurrentDestination
			?: return rejected
		if (
			pending.dispatch.id != commandId ||
			this.runtimeGeneration != runtimeGeneration ||
			pending.lastDispatchedGeneration != runtimeGeneration ||
			query.foliateSessionId == ReaderUnboundFoliateSessionId ||
			query.foliateSessionId != foliateSessionId
		) return rejected
		val resolved = event as? ReaderBridgeEvent.CurrentDestinationResolved
		if (resolved != null && (
			resolved.foliateSessionId != query.foliateSessionId ||
			resolved.destinationCommitIdentity.foliateSessionId != query.foliateSessionId
		)) return rejected
		return ReaderCurrentDestinationConsumptionStep(
			copy(pendingCommands = pendingCommands.drop(1)), resolved,
			pending.currentDestinationContinuation,
			resolved?.let { ReaderCurrentDestinationQueryOutcome.Resolved(it) }
				?: ReaderCurrentDestinationQueryOutcome.Failed(ReaderCurrentDestinationQueryFailure.QueryCommandFailed)
		)
	}

	internal fun enqueueCurrentDestinationQuery(
		dispatch: ReaderBridgeDispatchCommand,
		continuation: ReaderCurrentDestinationContinuation,
		requiresDispatchedOpening: Boolean = false
	): ReaderWebCommandDispatchState? {
		val query = dispatch.command as? ReaderBridgeCommand.ResolveCurrentDestination ?: return null
		if (publicationKey == null || runtimeGeneration == null ||
			query.foliateSessionId == ReaderUnboundFoliateSessionId || query.foliateSessionId != foliateSessionId ||
			pendingCommands.any { it.dispatch.id == dispatch.id || it.currentDestinationContinuation === continuation }) return null
		return copy(pendingCommands = pendingCommands + ReaderWebPendingCommand(
			dispatch = dispatch, currentDestinationContinuation = continuation,
			requiresDispatchedOpening = requiresDispatchedOpening))
	}

	internal fun retireCurrentDestinationQueryExact(
		continuation: ReaderCurrentDestinationContinuation
	): ReaderWebCommandDispatchState = retireCurrentDestinationQueries {
		it.currentDestinationContinuation === continuation
	}

	internal fun cancelCurrentDestinationQuery(commandId: String): ReaderWebCommandDispatchState =
		retireCurrentDestinationQueries { it.dispatch.id == commandId }

	internal fun closeCurrentDestinationQueries(): ReaderWebCommandDispatchState =
		retireCurrentDestinationQueries { true }

	private fun retireCurrentDestinationQueries(
		matches: (ReaderWebPendingCommand) -> Boolean
	): ReaderWebCommandDispatchState {
		val retained = pendingCommands.filterNot {
			it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination && matches(it)
		}
		return if (retained.size == pendingCommands.size) this else copy(pendingCommands = retained)
	}
}

internal class ReaderCurrentDestinationConsumptionStep(
	val state: ReaderWebCommandDispatchState,
	val accepted: ReaderBridgeEvent.CurrentDestinationResolved?,
	val continuation: ReaderCurrentDestinationContinuation? = null,
	val outcome: ReaderCurrentDestinationQueryOutcome? = null
)

data class ReaderWebCommandDispatchStep(
	val state: ReaderWebCommandDispatchState,
	val commands: List<ReaderBridgeDispatchCommand>,
	val retiredCurrentDestinationContinuations: List<ReaderCurrentDestinationContinuation> = emptyList(),
	val retiredInstalledPageTurnContinuations: List<ReaderInstalledPageTurnContinuation> = emptyList()
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
	rawTextProvenanceDescriptors: List<ReaderRawTextProvenanceDescriptor> = emptyList(),
	// Initial producer prepares the authentic opening/session in this same ledger before
	// JS Ready. No command is marked or returned for dispatch until the real ready route.
	dispatchHead: Boolean = true
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
			pendingCommands = nextState.pendingCommands + commands.zip(commandKeys).filter { (command, _) ->
				// A query belongs to its real open-dispatch session, not a replayed command key.
				command !is ReaderBridgeCommand.GoToVisualPage &&
				(command !is ReaderBridgeCommand.ResolveCurrentDestination ||
					(command.foliateSessionId != ReaderUnboundFoliateSessionId &&
						command.foliateSessionId == runtimeSessionId))
			}.map { (command, key) ->
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
		?.takeIf { dispatchHead && it.lastDispatchedGeneration != runtimeGeneration }
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
		commands = commands,
		retiredCurrentDestinationContinuations = if (publicationChanged || generationChanged) {
			pendingCommands.mapNotNull { it.currentDestinationContinuation }
		} else emptyList(),
		retiredInstalledPageTurnContinuations = if (publicationChanged || generationChanged) {
			pendingCommands.mapNotNull { it.installedPageTurnContinuation }
		} else emptyList()
	)
}
