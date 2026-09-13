package paige.navic.ui.screens.reader

import android.os.Looper
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionFactKind
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionJournal
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.ReaderTransitionOutcome
import paige.navic.reader.ReaderTransitionPhaseKind
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.deadlinePolicy

internal enum class ReaderTransitionFactClassification {
	CurrentTransition,
	Untagged,
	StaleTransition,
	NoActiveTransition
}

internal enum class ReaderTransitionCommandKind {
	RequestSemanticSynchronization,
	RequestRasterPreparation,
	ReserveDeck,
	RequestFramePresentation,
	ApplyInputLease,
	ReleaseResource,
	CancelOwnedWork
}

internal enum class ReaderTransitionOutcomeKind { Succeeded, Failed, Cancelled, Deferred }

internal enum class ReaderTransitionCoordinatorObservationKind {
	TransitionRegistered,
	FactProcessed,
	DeadlineScheduled,
	DeadlineCancelled
}

internal data class ReaderTransitionCoordinatorObservation(
	val kind: ReaderTransitionCoordinatorObservationKind,
	val factKind: ReaderTransitionFactKind? = null
)

internal data class ReaderTransitionShadowPrediction(
	val factKind: ReaderTransitionFactKind,
	val classification: ReaderTransitionFactClassification,
	val previousPhase: ReaderTransitionPhaseKind?,
	val nextPhase: ReaderTransitionPhaseKind?,
	val commandKinds: Set<ReaderTransitionCommandKind>,
	val outcome: ReaderTransitionOutcomeKind?
)

internal data class ReaderTransitionReleaseLedgerSnapshot(
	val ownedCount: Int,
	val issuedCount: Int,
	val releasedCount: Int
)

internal enum class ReaderTransitionResourceState {
	Owned,
	ReleaseCommandIssued,
	Released
}

internal enum class ReaderTransitionRetirementFenceState { Inactive, Active }

internal data class ReaderTransitionReleaseLedgerRetentionSnapshot(
	val activeStateCount: Int,
	val earlyConfirmationCount: Int,
	val terminalTombstoneCount: Int,
	val terminalTombstoneCapacity: Int,
	val retirementFenceState: ReaderTransitionRetirementFenceState
)

internal data class ReaderTransitionTerminalTombstoneSnapshot(
	val terminalTombstoneCount: Int,
	val terminalTombstoneCapacity: Int,
	val retirementFenceState: ReaderTransitionRetirementFenceState
)

private data class ReaderTransitionLifecycle(
	val readerSessionGeneration: Long,
	val coordinatorEpoch: Long
) : Comparable<ReaderTransitionLifecycle> {
	override fun compareTo(other: ReaderTransitionLifecycle): Int =
		compareValuesBy(
			this,
			other,
			ReaderTransitionLifecycle::readerSessionGeneration,
			ReaderTransitionLifecycle::coordinatorEpoch
		)
}

/**
 * Retains exact terminal identities only for the current callback horizon. Evicted identities are
 * fenced by their monotonic lifecycle and transition sequence, so old callbacks fail closed
 * without retaining their resource or lease objects indefinitely.
 */
internal class ReaderTransitionTerminalTombstones(
	private val capacity: Int = TerminalTombstoneCapacity
) {
	private val keys = ArrayDeque<ReaderTransitionResourceKey>()
	private val exactKeys = linkedSetOf<ReaderTransitionResourceKey>()
	private var lifecycle: ReaderTransitionLifecycle? = null
	private var retiredThroughSequence = 0L

	fun rejectsRegistration(key: ReaderTransitionResourceKey): Boolean =
		key in exactKeys || !admitLifecycle(key)

	fun containsOrFenced(key: ReaderTransitionResourceKey): Boolean =
		key in exactKeys || isFenced(key)

	fun record(key: ReaderTransitionResourceKey, fromActiveRegistration: Boolean = false): Boolean {
		if (key in exactKeys) return false
		if (!admitLifecycle(key)) return fromActiveRegistration
		while (keys.size == capacity) {
			val evicted = keys.removeFirst()
			exactKeys.remove(evicted)
			retiredThroughSequence = maxOf(retiredThroughSequence, evicted.transitionId.sequence)
		}
		keys.addLast(key)
		exactKeys += key
		return true
	}

	fun terminalSnapshot(): ReaderTransitionTerminalTombstoneSnapshot =
		ReaderTransitionTerminalTombstoneSnapshot(
			terminalTombstoneCount = keys.size,
			terminalTombstoneCapacity = capacity,
			retirementFenceState = if (lifecycle == null) {
				ReaderTransitionRetirementFenceState.Inactive
			} else {
				ReaderTransitionRetirementFenceState.Active
			}
		)

	fun snapshot(): ReaderTransitionReleaseLedgerRetentionSnapshot =
		terminalSnapshot().let { terminal ->
			ReaderTransitionReleaseLedgerRetentionSnapshot(
				activeStateCount = 0,
				earlyConfirmationCount = 0,
				terminalTombstoneCount = terminal.terminalTombstoneCount,
				terminalTombstoneCapacity = terminal.terminalTombstoneCapacity,
				retirementFenceState = terminal.retirementFenceState
			)
		}

	private fun admitLifecycle(key: ReaderTransitionResourceKey): Boolean {
		val candidate = key.lifecycle()
		val current = lifecycle
		if (current == null || candidate > current) {
			lifecycle = candidate
			retiredThroughSequence = 0L
			keys.clear()
			exactKeys.clear()
			return true
		}
		if (candidate < current) return false
		return key.transitionId.sequence > retiredThroughSequence
	}

	private fun isFenced(key: ReaderTransitionResourceKey): Boolean {
		val current = lifecycle ?: return false
		val candidate = key.lifecycle()
		return candidate < current ||
			(candidate == current && key.transitionId.sequence <= retiredThroughSequence)
	}

	private companion object {
		const val TerminalTombstoneCapacity = 32
	}
}

private fun ReaderTransitionResourceKey.lifecycle() = ReaderTransitionLifecycle(
	readerSessionGeneration = transitionId.readerSessionGeneration,
	coordinatorEpoch = transitionId.coordinatorEpoch
)

internal class ReaderTransitionReleaseLedger {
	private val states = linkedMapOf<ReaderTransitionResourceKey, ReaderTransitionResourceState>()
	private val terminalTombstones = ReaderTransitionTerminalTombstones()

	fun register(key: ReaderTransitionResourceKey): Boolean {
		if (key in states || terminalTombstones.rejectsRegistration(key)) return false
		states[key] = ReaderTransitionResourceState.Owned
		return true
	}

	fun requestRelease(key: ReaderTransitionResourceKey): ReaderTransitionCommand.ReleaseResource? {
		if (states[key] != ReaderTransitionResourceState.Owned) return null
		states[key] = ReaderTransitionResourceState.ReleaseCommandIssued
		return ReaderTransitionCommand.ReleaseResource(key.transitionId, key)
	}

	fun confirmReleased(key: ReaderTransitionResourceKey): Boolean {
		val activeState = states.remove(key)
		return terminalTombstones.record(key, fromActiveRegistration = activeState != null)
	}

	fun stateOf(key: ReaderTransitionResourceKey): ReaderTransitionResourceState? =
		states[key] ?: ReaderTransitionResourceState.Released.takeIf {
			terminalTombstones.containsOrFenced(key)
		}

	fun snapshot(): ReaderTransitionReleaseLedgerSnapshot = ReaderTransitionReleaseLedgerSnapshot(
		ownedCount = states.values.count { it == ReaderTransitionResourceState.Owned },
		issuedCount = states.values.count {
			it == ReaderTransitionResourceState.ReleaseCommandIssued
		},
		releasedCount = terminalTombstones.snapshot().terminalTombstoneCount
	)

	fun retentionSnapshot(): ReaderTransitionReleaseLedgerRetentionSnapshot =
		terminalTombstones.snapshot().copy(activeStateCount = states.size)
}

internal data class ReaderTransitionCoordinatorSnapshot(
	val mode: ReaderTransitionMode,
	val mailboxSize: Int,
	val advancing: Boolean,
	val maxAdvanceDepth: Int,
	val activeTransitionsRegistered: Int,
	val activeOperation: ReaderTransitionOperation?,
	val activePhase: ReaderTransitionPhaseKind?,
	val scheduledCallbackCount: Int,
	val factClassifications: Map<ReaderTransitionFactClassification, Int>,
	val shadowPredictions: List<ReaderTransitionShadowPrediction>,
	val lastOutcome: ReaderTransitionOutcomeKind?,
	val ownedResourceCount: Int,
	val releaseCommandIssuedCount: Int,
	val releasedResourceCount: Int,
	val releaseOnlySink: Boolean
) {
	companion object {
		const val MaxShadowPredictions = 32
	}
}

internal class ReaderResumableTransitionCoordinator(
	private val ports: ReaderResumableTransitionPorts,
	private val mode: ReaderTransitionMode,
	private var journal: ReaderTransitionJournal,
	private val releaseLedger: ReaderTransitionReleaseLedger = ReaderTransitionReleaseLedger(),
	private val onObservation: (ReaderTransitionCoordinatorObservation) -> Unit = {}
) {
	private val mailbox = ArrayDeque<ReaderTransitionFact>()
	private val classificationCounts = linkedMapOf<ReaderTransitionFactClassification, Int>()
	private val shadowPredictions = ArrayDeque<ReaderTransitionShadowPrediction>()
	private var advancing = false
	private var advanceDepth = 0
	private var maxAdvanceDepth = 0
	private var activeTransitionsRegistered = 0
	private var registeredTransitionId: ReaderTransitionId? = null
	private var deadlineRecord: ActiveDeadlineRecord? = null
	private var deadlineSlot: DeadlineSlot? = null
	private var nextDeadlineSlotToken = 1L
	private var releaseOnlySink = false

	init {
		journal.resourceKeys().forEach(releaseLedger::register)
	}

	fun enqueue(fact: ReaderTransitionFact) {
		check(ports.acceptsFact(fact)) {
			"Transition ports cannot accept this fact"
		}
		check(Looper.myLooper() == Looper.getMainLooper()) {
			"Reader transition facts must be enqueued on the main thread"
		}
		mailbox.addLast(fact)
		if (!advancing) advance()
	}

	fun snapshot(): ReaderTransitionCoordinatorSnapshot {
		val releaseSnapshot = releaseLedger.snapshot()
		return ReaderTransitionCoordinatorSnapshot(
			mode = mode,
			mailboxSize = mailbox.size,
			advancing = advancing,
			maxAdvanceDepth = maxAdvanceDepth,
			activeTransitionsRegistered = activeTransitionsRegistered,
			activeOperation = journal.active?.id?.operation,
			activePhase = journal.active?.phase?.kind,
			scheduledCallbackCount = if (deadlineSlot == null) 0 else 1,
			factClassifications = classificationCounts.toMap(),
			shadowPredictions = shadowPredictions.toList(),
			lastOutcome = journal.lastOutcome?.kind(),
			ownedResourceCount = releaseSnapshot.ownedCount,
			releaseCommandIssuedCount = releaseSnapshot.issuedCount,
			releasedResourceCount = releaseSnapshot.releasedCount,
			releaseOnlySink = releaseOnlySink
		)
	}

	fun releaseStateOf(key: ReaderTransitionResourceKey): ReaderTransitionResourceState? =
		releaseLedger.stateOf(key)

	private fun advance() {
		if (advancing) return
		advancing = true
		advanceDepth += 1
		maxAdvanceDepth = maxOf(maxAdvanceDepth, advanceDepth)
		try {
			while (mailbox.isNotEmpty()) {
				val fact = mailbox.removeFirst()
				val before = journal
				val classification = classify(fact, before)
				classificationCounts[classification] =
					classificationCounts.getOrElse(classification) { 0 } + 1
				val nowMillis = ports.clock.nowMillis()
				val registeredKey = fact.registeredResourceKeyOrNull()
				registeredKey?.let(releaseLedger::register)
				if (fact is ReaderTransitionFact.ResourceReleased) {
					releaseLedger.confirmReleased(fact.key)
				}
				val closingOperation = before.active?.id?.operation ==
					ReaderTransitionOperation.PublicationClose
				val reduction = if (releaseOnlySink) {
					paige.navic.reader.ReaderTransitionReduction(before, emptyList())
				} else {
					journal.reduce(fact, nowMillis)
				}

				// These assignments are the coordinator's publication barrier: callbacks may run
				// synchronously from either deadline registration or command issuance below.
				journal = reduction.state
				if (
					journal.active == null &&
					(fact is ReaderTransitionFact.PublicationClosed || closingOperation)
				) {
					releaseOnlySink = true
				}
				persistActiveRegistration()
				reconcileDeadline(nowMillis, before, fact, classification)
				val predictedCommands = buildList {
					addAll(reduction.commands)
					if (releaseOnlySink && registeredKey != null) {
						add(ReaderTransitionCommand.ReleaseResource(registeredKey.transitionId, registeredKey))
					}
				}
				recordShadowPrediction(fact, classification, before, predictedCommands)

				if (mode == ReaderTransitionMode.Active) {
					predictedCommands.forEach { predicted ->
						accountCommand(predicted)?.let { command -> ports.issue(command, ::enqueue) }
					}
				}
				onObservation(
					ReaderTransitionCoordinatorObservation(
						ReaderTransitionCoordinatorObservationKind.FactProcessed,
						fact.kind()
					)
				)
			}
		} finally {
			advanceDepth -= 1
			advancing = false
		}
	}

	private fun accountCommand(command: ReaderTransitionCommand): ReaderTransitionCommand? =
		if (command is ReaderTransitionCommand.ReleaseResource) {
			releaseLedger.register(command.key)
			releaseLedger.requestRelease(command.key)
		} else {
			command
		}

	private fun persistActiveRegistration() {
		val activeId = journal.active?.id
		if (activeId == registeredTransitionId) return
		registeredTransitionId = activeId
		if (activeId != null) {
			activeTransitionsRegistered += 1
			onObservation(
				ReaderTransitionCoordinatorObservation(
					ReaderTransitionCoordinatorObservationKind.TransitionRegistered
				)
			)
		}
	}

	private fun reconcileDeadline(
		nowMillis: Long,
		before: ReaderTransitionJournal,
		fact: ReaderTransitionFact,
		classification: ReaderTransitionFactClassification
	) {
		val active = journal.active
		if (active == null) {
			cancelDeadlineSlot()
			deadlineRecord = null
			return
		}

		val policy = active.id.operation.deadlinePolicy()
		var record = deadlineRecord
		if (record?.transitionId != active.id) {
			cancelDeadlineSlot()
			record = ActiveDeadlineRecord(
				transitionId = active.id,
				hardExpiresAtMillis = nowMillis.saturatingAdd(policy.hardMillis),
				lastProgressAtMillis = nowMillis
			)
		} else if (fact.isMatchingProgress(classification, before, journal)) {
			record = record.copy(
				lastProgressAtMillis = maxOf(record.lastProgressAtMillis, nowMillis)
			)
		}
		deadlineRecord = record

		val nextKey = DeadlineKey(active.id, active.phase.kind)
		val deadlineOwner = active.phase.contract.deadlineOwner
		check(deadlineOwner == active.id)
		val noProgressExpiresAtMillis = policy.noProgressMillis?.let { noProgressMillis ->
			record.lastProgressAtMillis.saturatingAdd(noProgressMillis)
		}
		val atMillis = noProgressExpiresAtMillis?.let { noProgressExpiry ->
			minOf(record.hardExpiresAtMillis, noProgressExpiry)
		} ?: record.hardExpiresAtMillis
		if (deadlineSlot?.key == nextKey && deadlineSlot?.atMillis == atMillis) return

		cancelDeadlineSlot()
		val token = nextDeadlineSlotToken
		check(token < Long.MAX_VALUE) { "Reader transition deadline slot token exhausted" }
		nextDeadlineSlotToken = token + 1L
		val slot = DeadlineSlot(nextKey, token, atMillis)
		deadlineSlot = slot
		onObservation(
			ReaderTransitionCoordinatorObservation(
				ReaderTransitionCoordinatorObservationKind.DeadlineScheduled
			)
		)
		val registration = ports.clock.schedule(atMillis) {
			val currentSlot = deadlineSlot
			if (currentSlot?.token == token && currentSlot.key == nextKey) {
				enqueue(ReaderTransitionFact.DeadlineExpired(deadlineOwner))
			}
		}
		if (deadlineSlot?.token == token) {
			slot.registration = registration
			if (registration == null) {
				enqueue(ReaderTransitionFact.DeadlineExpired(deadlineOwner))
			}
		} else {
			registration?.cancel()
		}
	}

	private fun cancelDeadlineSlot() {
		val obsolete = deadlineSlot ?: return
		deadlineSlot = null
		obsolete.registration?.cancel()
		onObservation(
			ReaderTransitionCoordinatorObservation(
				ReaderTransitionCoordinatorObservationKind.DeadlineCancelled
			)
		)
	}

	private fun recordShadowPrediction(
		fact: ReaderTransitionFact,
		classification: ReaderTransitionFactClassification,
		before: ReaderTransitionJournal,
		commands: List<ReaderTransitionCommand>
	) {
		if (mode != ReaderTransitionMode.Shadow) return
		if (shadowPredictions.size == ReaderTransitionCoordinatorSnapshot.MaxShadowPredictions) {
			shadowPredictions.removeFirst()
		}
		shadowPredictions.addLast(
			ReaderTransitionShadowPrediction(
				factKind = fact.kind(),
				classification = classification,
				previousPhase = before.active?.phase?.kind,
				nextPhase = journal.active?.phase?.kind,
				commandKinds = commands.mapTo(linkedSetOf()) { it.kind() },
				outcome = journal.lastOutcome?.kind()
			)
		)
	}

	private data class ActiveDeadlineRecord(
		val transitionId: ReaderTransitionId,
		val hardExpiresAtMillis: Long,
		val lastProgressAtMillis: Long
	)

	private data class DeadlineKey(
		val transitionId: ReaderTransitionId,
		val phase: ReaderTransitionPhaseKind
	)

	private class DeadlineSlot(
		val key: DeadlineKey,
		val token: Long,
		val atMillis: Long,
		var registration: ReaderTransitionClockRegistration? = null
	)
}

private fun ReaderTransitionFact.isMatchingProgress(
	classification: ReaderTransitionFactClassification,
	before: ReaderTransitionJournal,
	after: ReaderTransitionJournal
): Boolean {
	if (classification != ReaderTransitionFactClassification.CurrentTransition) return false
	val activeId = after.active?.id ?: return false
	if (before.active?.id != activeId) return false
	return this is ReaderTransitionFact.RasterProgress || before != after
}

private fun ReaderTransitionJournal.resourceKeys(): Set<ReaderTransitionResourceKey> = buildSet {
	committed?.resourceKey?.let(::add)
	active?.let { current ->
		current.predecessorResourceKey?.let(::add)
		addAll(current.ownedResourceKeys)
		current.admittedDeckKey?.let(::add)
		current.pendingPreparedDeckKey?.let(::add)
		current.successorResourceKey?.let(::add)
	}
}

private fun ReaderTransitionFact.registeredResourceKeyOrNull(): ReaderTransitionResourceKey? = when (this) {
	is ReaderTransitionFact.ResourceObserved -> key
	is ReaderTransitionFact.DeckReserved -> key
	is ReaderTransitionFact.DeckOwned -> key
	is ReaderTransitionFact.DeckPrepared -> key
	is ReaderTransitionFact.DeckRejected -> key
	is ReaderTransitionFact.PreparedFrame -> resourceKey
	is ReaderTransitionFact.CoverPostDraw -> resourceKey
	is ReaderTransitionFact.WebViewExposure -> resourceKey
	is ReaderTransitionFact.Intent,
	is ReaderTransitionFact.FoliateDestinationCommitted,
	is ReaderTransitionFact.SettlementAcknowledged,
	is ReaderTransitionFact.ViewportProfileReplaced,
	is ReaderTransitionFact.RasterProgress,
	is ReaderTransitionFact.RasterProven,
	is ReaderTransitionFact.RasterDeferred,
	is ReaderTransitionFact.RasterFailed,
	is ReaderTransitionFact.ResourceReleased,
	is ReaderTransitionFact.RendererCapacityAvailable,
	is ReaderTransitionFact.HostAvailable,
	is ReaderTransitionFact.PaginationProfileReady,
	is ReaderTransitionFact.RendererGenerationReady,
	is ReaderTransitionFact.VisibilityChanged,
	is ReaderTransitionFact.ResourceLost,
	is ReaderTransitionFact.DeadlineExpired,
	is ReaderTransitionFact.Retry,
	is ReaderTransitionFact.PublicationClosed -> null
}

internal fun ReaderTransitionFact.isTask4CoordinatorFact(): Boolean = when (this) {
	is ReaderTransitionFact.RasterProgress,
	is ReaderTransitionFact.RasterProven,
	is ReaderTransitionFact.RasterDeferred,
	is ReaderTransitionFact.RasterFailed,
	is ReaderTransitionFact.RendererCapacityAvailable,
	is ReaderTransitionFact.RendererGenerationReady,
	is ReaderTransitionFact.ResourceLost,
	is ReaderTransitionFact.DeadlineExpired,
	is ReaderTransitionFact.Retry,
	is ReaderTransitionFact.PublicationClosed -> true
	is ReaderTransitionFact.ResourceObserved -> key.isTask4ResourceKeyFor(transitionId)
	is ReaderTransitionFact.DeckReserved ->
		key.isTask4ResourceKeyFor(transitionId, ReaderTransitionResourceKind.Deck)
	is ReaderTransitionFact.DeckOwned ->
		key.isTask4ResourceKeyFor(transitionId, ReaderTransitionResourceKind.Deck)
	is ReaderTransitionFact.DeckPrepared ->
		key.isTask4ResourceKeyFor(transitionId, ReaderTransitionResourceKind.Deck)
	is ReaderTransitionFact.DeckRejected ->
		key.isTask4ResourceKeyFor(transitionId, ReaderTransitionResourceKind.Deck)
	is ReaderTransitionFact.ResourceReleased ->
		key.isTask4ResourceKeyFor(transitionId)
	is ReaderTransitionFact.Intent,
	is ReaderTransitionFact.FoliateDestinationCommitted,
	is ReaderTransitionFact.SettlementAcknowledged,
	is ReaderTransitionFact.ViewportProfileReplaced,
	is ReaderTransitionFact.HostAvailable,
	is ReaderTransitionFact.PaginationProfileReady,
	is ReaderTransitionFact.PreparedFrame,
	is ReaderTransitionFact.CoverPostDraw,
	is ReaderTransitionFact.WebViewExposure,
	is ReaderTransitionFact.VisibilityChanged -> false
}

private fun ReaderTransitionResourceKey.isTask4ResourceKeyFor(
	factTransitionId: ReaderTransitionId,
	vararg allowedKinds: ReaderTransitionResourceKind
): Boolean {
	if (transitionId != factTransitionId) return false
	return if (allowedKinds.isEmpty()) {
		kind == ReaderTransitionResourceKind.Deck || kind == ReaderTransitionResourceKind.Raster
	} else {
		kind in allowedKinds
	}
}

private fun Long.saturatingAdd(increment: Long): Long =
	if (this > Long.MAX_VALUE - increment) Long.MAX_VALUE else this + increment

private fun classify(
	fact: ReaderTransitionFact,
	journal: ReaderTransitionJournal
): ReaderTransitionFactClassification {
	val transitionId = fact.transitionId ?: return ReaderTransitionFactClassification.Untagged
	val activeId = journal.active?.id ?: return ReaderTransitionFactClassification.NoActiveTransition
	return if (transitionId == activeId) {
		ReaderTransitionFactClassification.CurrentTransition
	} else {
		ReaderTransitionFactClassification.StaleTransition
	}
}

private fun ReaderTransitionFact.kind(): ReaderTransitionFactKind = when (this) {
	is ReaderTransitionFact.Intent -> ReaderTransitionFactKind.Intent
	is ReaderTransitionFact.FoliateDestinationCommitted -> ReaderTransitionFactKind.FoliateDestinationCommitted
	is ReaderTransitionFact.SettlementAcknowledged -> ReaderTransitionFactKind.SettlementAcknowledged
	is ReaderTransitionFact.ViewportProfileReplaced -> ReaderTransitionFactKind.ViewportProfileReplaced
	is ReaderTransitionFact.RasterProgress -> ReaderTransitionFactKind.RasterProgress
	is ReaderTransitionFact.RasterProven -> ReaderTransitionFactKind.RasterProven
	is ReaderTransitionFact.RasterDeferred -> ReaderTransitionFactKind.RasterDeferred
	is ReaderTransitionFact.RasterFailed -> ReaderTransitionFactKind.RasterFailed
	is ReaderTransitionFact.ResourceObserved -> ReaderTransitionFactKind.ResourceObserved
	is ReaderTransitionFact.DeckReserved -> ReaderTransitionFactKind.DeckReserved
	is ReaderTransitionFact.DeckOwned -> ReaderTransitionFactKind.DeckOwned
	is ReaderTransitionFact.DeckPrepared -> ReaderTransitionFactKind.DeckPrepared
	is ReaderTransitionFact.DeckRejected -> ReaderTransitionFactKind.DeckRejected
	is ReaderTransitionFact.ResourceReleased -> ReaderTransitionFactKind.ResourceReleased
	is ReaderTransitionFact.RendererCapacityAvailable -> ReaderTransitionFactKind.RendererCapacityAvailable
	is ReaderTransitionFact.HostAvailable -> ReaderTransitionFactKind.HostAvailable
	is ReaderTransitionFact.PaginationProfileReady -> ReaderTransitionFactKind.PaginationProfileReady
	is ReaderTransitionFact.RendererGenerationReady -> ReaderTransitionFactKind.RendererGenerationReady
	is ReaderTransitionFact.PreparedFrame -> ReaderTransitionFactKind.PreparedFrame
	is ReaderTransitionFact.CoverPostDraw -> ReaderTransitionFactKind.CoverPostDraw
	is ReaderTransitionFact.WebViewExposure -> ReaderTransitionFactKind.WebViewExposure
	is ReaderTransitionFact.VisibilityChanged -> ReaderTransitionFactKind.VisibilityChanged
	is ReaderTransitionFact.ResourceLost -> ReaderTransitionFactKind.ResourceLost
	is ReaderTransitionFact.DeadlineExpired -> ReaderTransitionFactKind.DeadlineExpired
	is ReaderTransitionFact.Retry -> ReaderTransitionFactKind.Retry
	is ReaderTransitionFact.PublicationClosed -> ReaderTransitionFactKind.PublicationClosed
}

private fun ReaderTransitionCommand.kind(): ReaderTransitionCommandKind = when (this) {
	is ReaderTransitionCommand.RequestSemanticSynchronization ->
		ReaderTransitionCommandKind.RequestSemanticSynchronization
	is ReaderTransitionCommand.RequestRasterPreparation -> ReaderTransitionCommandKind.RequestRasterPreparation
	is ReaderTransitionCommand.ReserveDeck -> ReaderTransitionCommandKind.ReserveDeck
	is ReaderTransitionCommand.RequestFramePresentation -> ReaderTransitionCommandKind.RequestFramePresentation
	is ReaderTransitionCommand.ApplyInputLease -> ReaderTransitionCommandKind.ApplyInputLease
	is ReaderTransitionCommand.ReleaseResource -> ReaderTransitionCommandKind.ReleaseResource
	is ReaderTransitionCommand.CancelOwnedWork -> ReaderTransitionCommandKind.CancelOwnedWork
}

private fun ReaderTransitionOutcome.kind(): ReaderTransitionOutcomeKind = when (this) {
	is ReaderTransitionOutcome.Succeeded -> ReaderTransitionOutcomeKind.Succeeded
	is ReaderTransitionOutcome.Failed -> ReaderTransitionOutcomeKind.Failed
	is ReaderTransitionOutcome.Cancelled -> ReaderTransitionOutcomeKind.Cancelled
	is ReaderTransitionOutcome.Deferred -> ReaderTransitionOutcomeKind.Deferred
}
