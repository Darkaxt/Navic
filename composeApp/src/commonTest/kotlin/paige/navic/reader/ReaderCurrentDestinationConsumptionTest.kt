package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertTrue

/** Prospective owning behavior RED. Uses the actual pending reducer, not an executor receipt. */
class ReaderCurrentDestinationConsumptionTest {
	private val open = ReaderBridgeCommand.OpenPublication(
		url = "https://reader.test/synthetic.epub",
		foliateSessionId = ReaderUnboundFoliateSessionId,
		startLocator = ReaderLocator(progress = 0.25)
	)
	private val locator = ReaderLocator(
		href = "synthetic.xhtml", cfi = "synthetic-cfi", progress = 0.25,
		pageIndex = 2, pageCount = 8, chapterProgress = 0.5,
		chapterPageIndex = 2, chapterPageCount = 4
	)

	private fun opened(): ReaderWebCommandDispatchStep =
		ReaderWebCommandDispatchState().commandsForReadyReaderRuntime(
			runtimeGeneration = 0, publicationKey = "synthetic-publication", openCommand = open,
			command = null, commandKey = 0L
		)

	private fun queryStep(queued: Boolean = false): ReaderWebCommandDispatchStep {
		val initial = opened()
		val state = if (queued) initial.state else initial.state.acknowledge(initial.commands.single().id)
		return state.commandsForReadyReaderRuntime(
			runtimeGeneration = 0, publicationKey = "synthetic-publication", openCommand = open,
			command = ReaderBridgeCommand.ResolveCurrentDestination(checkNotNull(state.foliateSessionId)),
			commandKey = 7L
		)
	}

	private fun result(step: ReaderWebCommandDispatchStep): ReaderBridgeEvent.CurrentDestinationResolved {
		val pending = step.state.pendingCommands.single {
			it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination
		}
		val session = (pending.dispatch.command as ReaderBridgeCommand.ResolveCurrentDestination).foliateSessionId
		return ReaderBridgeEvent.CurrentDestinationResolved(
			pending.dispatch.id, session, ReaderDestinationCommitIdentity(session, 1L), locator
		)
	}

	private fun assertRejectedUnchanged(
		state: ReaderWebCommandDispatchState,
		event: ReaderBridgeEvent,
		generation: Int = 0
	) {
		val step = state.consumeCurrentDestinationResult(event, generation)
		assertTrue(step.accepted == null, "Inapplicable event cannot produce an accepted semantic result")
		assertTrue(step.state == state, "Inapplicable event must preserve exact pending state")
	}

	@Test
	fun exactDispatchedResultBeforeAckReturnsTypedResultAndRetiresOnlyQuery() {
		val query = queryStep()
		val result = result(query)
		val withOrdinary = query.state.commandsForReadyReaderRuntime(
			0, "synthetic-publication", open, ReaderBridgeCommand.ClearOverlay, 8L
		).state
		val consumed = withOrdinary.consumeCurrentDestinationResult(result, 0)
		assertTrue(consumed.accepted == result, "Exact dispatched result must be returned without promotion")
		assertTrue(consumed.state.pendingCommands == withOrdinary.pendingCommands.filterNot {
			it.dispatch.id == result.commandId
		}, "Consumption must retire only the exact query")
		assertTrue(consumed.state.lastKnownLocator == withOrdinary.lastKnownLocator,
			"Query result must not become ordinary observed location")
		assertTrue(consumed.state == consumed.state.acknowledge(result.commandId),
			"Late transport ACK cannot restore or retire another command")
	}

	@Test
	fun exactDispatchedResultAfterAckStillReturnsTypedResultAndRetiresQuery() {
		val query = queryStep()
		val result = result(query)
		val acknowledged = query.state.acknowledge(result.commandId)
		val consumed = acknowledged.consumeCurrentDestinationResult(result, 0)
		assertTrue(consumed.accepted == result, "ACK must not lose semantic query custody")
		assertTrue(consumed.state.pendingCommands.isEmpty(), "Accepted result must retire its query")
	}

	@Test
	fun duplicateResultIsAcceptedExactlyOnce() {
		val query = queryStep()
		val result = result(query)
		val first = query.state.consumeCurrentDestinationResult(result, 0)
		assertTrue(first.accepted == result, "First exact result must be accepted")
		assertRejectedUnchanged(first.state, result)
	}

	@Test
	fun resultBeforeAckAllowsNextOrdinaryCommandWithoutQueryReplay() {
		val query = queryStep()
		val result = result(query)
		val queued = query.state.commandsForReadyReaderRuntime(
			0, "synthetic-publication", open, ReaderBridgeCommand.ClearOverlay, 8L
		)
		val consumed = queued.state.consumeCurrentDestinationResult(result, 0)
		assertTrue(consumed.accepted == result, "Current query must complete before queue progression")
		val next = consumed.state.commandsForReadyReaderRuntime(
			0, "synthetic-publication", open, ReaderBridgeCommand.ClearOverlay, 8L
		)
		assertTrue(next.commands.map { it.command } == listOf(ReaderBridgeCommand.ClearOverlay),
			"Semantic completion must unblock the actual next pending ordinary command")
	}

	@Test
	fun unknownAndWrongCommandIdResultsDoNotConsumeActualQuery() {
		val query = queryStep()
		val result = result(query)
		assertRejectedUnchanged(query.state, result.copy(commandId = "unknown-query"))
		assertRejectedUnchanged(query.state, result.copy(commandId = "reader-open-1"))
		val withOrdinary = query.state.commandsForReadyReaderRuntime(
			0, "synthetic-publication", open, ReaderBridgeCommand.ClearOverlay, 8L
		).state
		val ordinaryId = withOrdinary.pendingCommands.single {
			it.dispatch.command == ReaderBridgeCommand.ClearOverlay
		}.dispatch.id
		assertRejectedUnchanged(withOrdinary, result.copy(commandId = ordinaryId))
	}

	@Test
	fun resultForQueuedUndispatchedQueryCannotBeAccepted() {
		val query = queryStep(queued = true)
		assertTrue(query.commands.isEmpty(), "Fixture must use the actual blocked queue")
		val pending = query.state.pendingCommands.single {
			it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination
		}
		assertTrue(pending.lastDispatchedGeneration == null, "Queued query has no real dispatch proof")
		assertRejectedUnchanged(query.state, result(query))
	}

	@Test
	fun wrongEnvelopeSessionCannotConsumeActualQuery() {
		val query = queryStep()
		val wrongSession = "foliate-synthetic-wrong"
		assertRejectedUnchanged(query.state, result(query).copy(
			foliateSessionId = wrongSession,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(wrongSession, 1L)
		))
	}

	@Test
	fun callbackFromWrongRuntimeGenerationCannotConsumeActualQuery() {
		val query = queryStep()
		assertRejectedUnchanged(query.state, result(query), generation = 1)
	}

	@Test
	fun currentSessionMismatchCannotBorrowPendingQuerySession() {
		val query = queryStep()
		// Negative-only inconsistency: does not manufacture a positive dispatched owner.
		val wrongCurrent = query.state.copy(foliateSessionId = "foliate-synthetic-replaced")
		assertRejectedUnchanged(wrongCurrent, result(query))
	}

	@Test
	fun currentGenerationMismatchCannotBorrowPendingDispatchGeneration() {
		val query = queryStep()
		val wrongCurrent = query.state.copy(runtimeGeneration = 1)
		assertRejectedUnchanged(wrongCurrent, result(query), generation = 1)
	}

	@Test
	fun recreatedRuntimeDoesNotReplayOldSessionQueryOrAcceptItsLateResult() {
		val query = queryStep()
		val result = result(query)
		val recreated = query.state.commandsForReadyReaderRuntime(
			1, "synthetic-publication", open, query.commands.single().command, 7L
		)
		assertTrue(recreated.state.pendingCommands.none {
			it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination
		}, "Recreation must not replay an old current-destination query into a new source")
		assertRejectedUnchanged(recreated.state, result, generation = 1)
	}

	@Test
	fun reopenedPublicationDoesNotReplayOldSessionQueryOrAcceptItsLateResult() {
		val query = queryStep()
		val reopened = query.state.commandsForReadyReaderRuntime(
			0, "synthetic-publication-reopened", open, query.commands.single().command, 7L
		)
		assertTrue(reopened.state.pendingCommands.none {
			it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination
		}, "Publication replacement must not reuse an old query's session")
		assertRejectedUnchanged(reopened.state, result(query))
	}

	@Test
	fun ordinaryLocationChangedIsNeverPromotedToQueryResult() {
		val query = queryStep()
		val result = result(query)
		assertRejectedUnchanged(query.state, ReaderBridgeEvent.LocationChanged(
			locator = locator, foliateSessionId = result.foliateSessionId,
			destinationCommitIdentity = result.destinationCommitIdentity
		))
	}

	@Test
	fun transportAckEventIsNeverSemanticProof() {
		val query = queryStep()
		assertRejectedUnchanged(query.state, ReaderBridgeEvent.CommandAcknowledged(result(query).commandId))
	}

	@Test
	fun matchingFailureRetiresDispatchedQueryAndRejectsLateResult() {
		val query = queryStep()
		val result = result(query)
		val failed = query.state.consumeCurrentDestinationResult(ReaderBridgeEvent.CommandFailed(result.commandId), 0)
		assertTrue(failed.accepted == null, "Failure cannot supply semantic success")
		assertTrue(failed.state.pendingCommands.isEmpty(), "Matching failure must retire exact dispatched query")
		assertRejectedUnchanged(failed.state, result)
	}

	@Test
	fun matchingFailureAfterAckRetiresQueryWithoutSemanticSuccess() {
		val query = queryStep()
		val result = result(query)
		val acknowledged = query.state.acknowledge(result.commandId)
		val failed = acknowledged.consumeCurrentDestinationResult(ReaderBridgeEvent.CommandFailed(result.commandId), 0)
		assertTrue(failed.accepted == null, "Post-ACK failure must not fabricate semantic success")
		assertTrue(failed.state.pendingCommands.isEmpty(), "Transport ACK cannot prevent failure retirement")
		assertRejectedUnchanged(failed.state, result)
	}

	@Test
	fun wrongAndQueuedFailureCallbacksCannotRetireQuery() {
		val query = queryStep()
		assertRejectedUnchanged(query.state, ReaderBridgeEvent.CommandFailed("unknown-query"))
		assertRejectedUnchanged(query.state, ReaderBridgeEvent.CommandFailed(result(query).commandId), generation = 1)
		val queued = queryStep(queued = true)
		assertRejectedUnchanged(queued.state, ReaderBridgeEvent.CommandFailed(result(queued).commandId))
	}

	@Test
	fun cancellationRetiresOnlyExactQueryAndRejectsLateResult() {
		val query = queryStep()
		val result = result(query)
		val withOrdinary = query.state.commandsForReadyReaderRuntime(
			0, "synthetic-publication", open, ReaderBridgeCommand.ClearOverlay, 8L
		).state
		val cancelled = withOrdinary.cancelCurrentDestinationQuery(result.commandId)
		assertTrue(cancelled.pendingCommands == withOrdinary.pendingCommands.filterNot {
			it.dispatch.id == result.commandId
		}, "Cancellation must retire only exact query custody")
		assertRejectedUnchanged(cancelled, result)
		assertTrue(cancelled == cancelled.cancelCurrentDestinationQuery(result.commandId),
			"Duplicate cancellation must be inert")
	}

	@Test
	fun queuedQueryCanBeCancelledWithoutClaimingItWasDispatched() {
		val query = queryStep(queued = true)
		val result = result(query)
		val cancelled = query.state.cancelCurrentDestinationQuery(result.commandId)
		assertTrue(cancelled.pendingCommands == query.state.pendingCommands.filterNot {
			it.dispatch.id == result.commandId
		}, "Explicit cancel must retire a queued query without synthetic dispatch proof")
		assertRejectedUnchanged(cancelled, result)
	}

	@Test
	fun newlyBoundQueryAfterRealRuntimeRecreationRemainsAdmissible() {
		val oldQuery = queryStep()
		val recreated = oldQuery.state.commandsForReadyReaderRuntime(
			1, "synthetic-publication", open, oldQuery.commands.single().command, 7L
		)
		val ready = recreated.state.acknowledge(recreated.commands.single().id)
		val fresh = ready.commandsForReadyReaderRuntime(
			1, "synthetic-publication", open,
			ReaderBridgeCommand.ResolveCurrentDestination(checkNotNull(ready.foliateSessionId)), 8L
		)
		val resolved = result(fresh)
		val consumed = fresh.state.consumeCurrentDestinationResult(resolved, 1)
		assertTrue(consumed.accepted === resolved, "A new query bound to the actual ready runtime must return the original typed result")
		assertTrue(consumed.state.pendingCommands.isEmpty(), "Fresh query consumption must retire exact current custody")
		assertRejectedUnchanged(consumed.state, result(oldQuery), generation = 1)
	}

	@Test
	fun failureRetirementPreservesAndUnblocksActualOrdinaryWork() {
		val query = queryStep()
		val queued = query.state.commandsForReadyReaderRuntime(
			0, "synthetic-publication", open, ReaderBridgeCommand.ClearOverlay, 8L
		)
		val resolved = result(query)
		val failed = queued.state.consumeCurrentDestinationResult(ReaderBridgeEvent.CommandFailed(resolved.commandId), 0)
		assertTrue(failed.accepted == null, "Failure must never supply semantic success")
		assertTrue(failed.state.pendingCommands == queued.state.pendingCommands.drop(1), "Failure must preserve exact ordinary pending work")
		val next = failed.state.commandsForReadyReaderRuntime(
			0, "synthetic-publication", open, ReaderBridgeCommand.ClearOverlay, 8L
		)
		assertTrue(next.commands.map { it.command } == listOf(ReaderBridgeCommand.ClearOverlay), "Exact failed query retirement must unblock ordinary dispatch")
	}

	@Test
	fun closeAndQueryCancellationNeverDropOrdinaryPendingWork() {
		val query = queryStep(queued = true)
		val queued = query.state.commandsForReadyReaderRuntime(
			0, "synthetic-publication", open, ReaderBridgeCommand.ClearOverlay, 8L
		).state
		val ordinaryId = queued.pendingCommands.single { it.dispatch.command == ReaderBridgeCommand.ClearOverlay }.dispatch.id
		assertTrue(queued.cancelCurrentDestinationQuery(ordinaryId) === queued, "Query cancellation cannot retire an ordinary command")
		val closed = queued.closeCurrentDestinationQueries()
		assertTrue(closed.pendingCommands == queued.pendingCommands.filterNot {
			it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination
		}, "Closing query custody must preserve all exact ordinary work")
		assertTrue(closed.closeCurrentDestinationQueries() === closed, "Duplicate query close must retain the same state")
	}

	@Test
	fun unknownCancellationPreservesPendingQuery() {
		val query = queryStep()
		assertTrue(query.state == query.state.cancelCurrentDestinationQuery("unknown-query"),
			"Unknown cancellation must not retire another query")
	}

	@Test
	fun closeRetiresQueryCustodyAndRejectsLateResultWithoutSuccess() {
		val query = queryStep()
		val result = result(query)
		val closed = query.state.closeCurrentDestinationQueries()
		assertTrue(closed.pendingCommands.none {
			it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination
		}, "Closed source must retire all remaining current-destination queries")
		assertRejectedUnchanged(closed, result)
		assertTrue(closed == closed.closeCurrentDestinationQueries(), "Duplicate close must be inert")
	}
}
