package paige.navic.ui.screens.reader

import android.os.Handler
import android.os.Looper
import paige.navic.reader.ReaderActiveTransition
import paige.navic.reader.ReaderCommittedPresentation
import paige.navic.reader.ReaderInitialCommittedPresentationOrigin
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderOwnerAndInputPublicationResult
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventOrigin
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationSemanticReceipt
import paige.navic.reader.ReaderPresentationSemanticReceiptConsumption
import paige.navic.reader.ReaderReleaseOnlyCleanupDeadlineStatus
import paige.navic.reader.ReaderReleaseOnlySemanticAuthority
import paige.navic.reader.ReaderSaturatingCallbackCount
import paige.navic.reader.ReaderSemanticPortContractViolationReason
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionCommandRejectionReason
import paige.navic.reader.ReaderTransitionCommandStage
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionFactKind
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionJournal
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.ReaderTransitionOutcome
import paige.navic.reader.ReaderTransitionPhaseKind
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionResourceRegistration
import paige.navic.reader.ReaderResourceRetirementOrder
import paige.navic.reader.ReaderTransitionResourceOwnerId
import paige.navic.reader.deadlinePolicy
import paige.navic.reader.pendingStageOrNull
import paige.navic.reader.reduceTrustedSemanticPortContractViolation
import paige.navic.reader.retainTrustedSemanticAuthority

internal enum class ReaderTransitionFactClassification {
	CurrentTransition,
	Untagged,
	StaleTransition,
	NoActiveTransition
}

internal enum class ReaderTransitionCommandKind {
	RequestSemanticSynchronization,
	AllocateMaterialBinding,
	RequestRasterPreparation,
	ReserveDeck,
	PrepareFrameTarget,
	RequestFramePresentation,
	PublishRetainedOwnerAndInputLease,
	CommitOwnerAndInputLease,
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
	val retirementFenceState: ReaderTransitionRetirementFenceState,
	val contiguousReleasedThrough: Long = 0L,
	val outOfOrderReleasedCount: Int = 0
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
			val evictedSequence = when (val owner = evicted.ownerId) {
				is ReaderTransitionResourceOwnerId.TransitionOwned -> owner.transitionId.sequence
				is ReaderTransitionResourceOwnerId.AdoptedPredecessor -> continue
			}
			retiredThroughSequence = maxOf(retiredThroughSequence, evictedSequence)
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
		val owner = when (val candidate = key.ownerId) {
			is ReaderTransitionResourceOwnerId.TransitionOwned -> candidate.transitionId
			is ReaderTransitionResourceOwnerId.AdoptedPredecessor -> return false
		}
		val candidate = owner.lifecycle()
		val current = lifecycle
		if (current == null || candidate > current) {
			lifecycle = candidate
			retiredThroughSequence = 0L
			keys.clear()
			exactKeys.clear()
			return true
		}
		if (candidate < current) return false
		return owner.sequence > retiredThroughSequence
	}

	private fun isFenced(key: ReaderTransitionResourceKey): Boolean {
		val owner = when (val candidate = key.ownerId) {
			is ReaderTransitionResourceOwnerId.TransitionOwned -> candidate.transitionId
			is ReaderTransitionResourceOwnerId.AdoptedPredecessor -> return false
		}
		val current = lifecycle ?: return false
		val candidate = owner.lifecycle()
		return candidate < current ||
			(candidate == current && owner.sequence <= retiredThroughSequence)
	}

	private companion object {
		const val TerminalTombstoneCapacity = 32
	}
}

private fun paige.navic.reader.ReaderTransitionId.lifecycle(): ReaderTransitionLifecycle =
	ReaderTransitionLifecycle(
		readerSessionGeneration = readerSessionGeneration,
		coordinatorEpoch = coordinatorEpoch
	)

internal class ReaderTransitionReleaseLedger {
	private val states = linkedMapOf<ReaderTransitionResourceKey, ReaderTransitionResourceState>()
	private val registrations = linkedMapOf<ReaderTransitionResourceKey, ReaderTransitionResourceRegistration>()
	private val explicitRegistrations = linkedSetOf<ReaderTransitionResourceKey>()
	private val terminalTombstones = ReaderTransitionTerminalTombstones()
	private var registrationLifecycle: Pair<Long, Long>? = null
	private var nextRetirementSequence = 1L
	private var contiguousReleasedThrough = 0L
	private val outOfOrderReleased = sortedSetOf<Long>()

	fun register(key: ReaderTransitionResourceKey): Boolean {
		val owner = key.ownerId as? ReaderTransitionResourceOwnerId.TransitionOwned ?: return false
		if (key in states || terminalTombstones.rejectsRegistration(key)) return false
		val id = owner.transitionId
		registrations[key] = ReaderTransitionResourceRegistration(
			key,
			ReaderResourceRetirementOrder(
				id.readerSessionGeneration,
				id.coordinatorEpoch,
				id.sequence
			)
		)
		states[key] = ReaderTransitionResourceState.Owned
		return true
	}

	fun register(registration: ReaderTransitionResourceRegistration): Boolean {
		val key = registration.key
		val existing = registrations[key]
		if (existing != null) return existing == registration && false
		if (key in states) return false
		if (key.owningTransitionIdOrNull != null && terminalTombstones.rejectsRegistration(key)) return false
		if (!admitRetirementOrder(registration.retirementOrder)) return false
		registrations[key] = registration
		explicitRegistrations += key
		states[key] = ReaderTransitionResourceState.Owned
		return true
	}

	fun requestRelease(key: ReaderTransitionResourceKey): ReaderTransitionCommand.ReleaseResource? {
		if (states[key] != ReaderTransitionResourceState.Owned) return null
		val registration = registrations[key] ?: return null
		states[key] = ReaderTransitionResourceState.ReleaseCommandIssued
		return if (key in explicitRegistrations) {
			ReaderTransitionCommand.ReleaseResource(registration)
		} else {
			when (val owner = key.ownerId) {
				is ReaderTransitionResourceOwnerId.TransitionOwned ->
					ReaderTransitionCommand.ReleaseResource(owner.transitionId, key)
				is ReaderTransitionResourceOwnerId.AdoptedPredecessor -> null
			}
		}
	}

	fun confirmReleased(key: ReaderTransitionResourceKey): Boolean {
		val explicitRegistration = registrations[key]?.takeIf { key in explicitRegistrations }
		if (explicitRegistration != null) return confirmReleased(explicitRegistration)
		val activeState = states.remove(key)
		registrations.remove(key)
		val recorded = terminalTombstones.record(key, fromActiveRegistration = activeState != null)
		return recorded
	}

	fun confirmReleased(registration: ReaderTransitionResourceRegistration): Boolean {
		val key = registration.key
		if (registrations[key] != registration || key !in explicitRegistrations) return false
		val sequence = registration.retirementOrder.sequence
		if (
			sequence > contiguousReleasedThrough + 1L &&
			sequence !in outOfOrderReleased &&
			outOfOrderReleased.size >= 32
		) return false
		if (states.remove(key) == null) return false
		registrations.remove(key)
		explicitRegistrations.remove(key)
		val recorded = when (key.ownerId) {
			is ReaderTransitionResourceOwnerId.TransitionOwned ->
				terminalTombstones.record(key, fromActiveRegistration = true)
			is ReaderTransitionResourceOwnerId.AdoptedPredecessor -> true
		}
		if (!recorded) return false
		advanceRetirementFence(registration.retirementOrder.sequence)
		return true
	}

	fun stateOf(key: ReaderTransitionResourceKey): ReaderTransitionResourceState? =
		states[key] ?: ReaderTransitionResourceState.Released.takeIf {
			key.owningTransitionIdOrNull?.let { terminalTombstones.containsOrFenced(key) } == true
		}

	fun snapshot(): ReaderTransitionReleaseLedgerSnapshot = ReaderTransitionReleaseLedgerSnapshot(
		ownedCount = states.values.count { it == ReaderTransitionResourceState.Owned },
		issuedCount = states.values.count { it == ReaderTransitionResourceState.ReleaseCommandIssued },
		releasedCount = terminalTombstones.snapshot().terminalTombstoneCount
	)

	fun retentionSnapshot(): ReaderTransitionReleaseLedgerRetentionSnapshot =
		terminalTombstones.snapshot().copy(
			activeStateCount = states.size,
			contiguousReleasedThrough = contiguousReleasedThrough,
			outOfOrderReleasedCount = outOfOrderReleased.size
		)

	private fun allocateRetirementOrder(session: Long, epoch: Long): ReaderResourceRetirementOrder {
		val lifecycle = session to epoch
		if (registrationLifecycle != lifecycle) {
			registrationLifecycle = lifecycle
			nextRetirementSequence = 1L
			contiguousReleasedThrough = 0L
			outOfOrderReleased.clear()
		}
		val sequence = nextRetirementSequence
		check(sequence < Long.MAX_VALUE) { "Resource retirement order exhausted" }
		nextRetirementSequence += 1L
		return ReaderResourceRetirementOrder(session, epoch, sequence)
	}

	private fun admitRetirementOrder(order: ReaderResourceRetirementOrder): Boolean {
		val lifecycle = order.readerSessionGeneration to order.coordinatorEpoch
		val current = registrationLifecycle
		if (current == null || compareValuesBy(lifecycle, current, Pair<Long, Long>::first, Pair<Long, Long>::second) > 0) {
			registrationLifecycle = lifecycle
			nextRetirementSequence = order.sequence + 1L
			contiguousReleasedThrough = 0L
			outOfOrderReleased.clear()
			return true
		}
		if (lifecycle != current || order.sequence <= contiguousReleasedThrough || order.sequence in outOfOrderReleased) return false
		nextRetirementSequence = maxOf(nextRetirementSequence, order.sequence + 1L)
		return true
	}

	private fun advanceRetirementFence(sequence: Long) {
		if (sequence == contiguousReleasedThrough + 1L) {
			contiguousReleasedThrough = sequence
			while (outOfOrderReleased.remove(contiguousReleasedThrough + 1L)) {
				contiguousReleasedThrough += 1L
			}
		} else if (sequence > contiguousReleasedThrough + 1L) {
			check(outOfOrderReleased.size < 32) { "Resource retirement tombstone capacity exceeded" }
			outOfOrderReleased += sequence
		}
	}
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
	val consumedSettlementCount: Int,
	val releaseOnlySink: Boolean,
	val releaseOnlySemanticAuthorityRetained: Boolean,
	val releaseOnlyDeadlineStatus: ReaderReleaseOnlyCleanupDeadlineStatus?
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
	private val activationState: () -> ReaderSessionActivationState = {
		ReaderSessionActivationState.Legacy
	},
	private val onObservation: (ReaderTransitionCoordinatorObservation) -> Unit = {}
) {
	private data class Task6AuthoritativeExpiry(
		val fact: ReaderTransitionFact.DeadlineExpired,
		val registration: ReaderTask6FactOnlyTimerRegistration
	)

	private class Task6TimerOwnership {
		var primary: ReaderTask6FactOnlyTimerRegistration? = null
			private set
		var rollback: ReaderTask6FactOnlyTimerRegistration? = null
			private set
		private val cancellationFailures = linkedMapOf<
			ReaderTask6FactOnlyTimerRegistration,
			ReaderReleaseOnlyCleanupDeadlineStatus
		>()

		val registrations: List<ReaderTask6FactOnlyTimerRegistration>
			get() = listOfNotNull(primary, rollback).distinct()
		val failures: Collection<ReaderReleaseOnlyCleanupDeadlineStatus>
			get() = cancellationFailures.values
		val count: Int
			get() = registrations.size

		fun contains(registration: ReaderTask6FactOnlyTimerRegistration): Boolean =
			primary == registration || rollback == registration

		fun failure(
			registration: ReaderTask6FactOnlyTimerRegistration
		): ReaderReleaseOnlyCleanupDeadlineStatus? = cancellationFailures[registration]

		fun trackPrimary(registration: ReaderTask6FactOnlyTimerRegistration) {
			check(primary == null || primary == registration) {
				"Task 6 primary timer ownership is bounded to one exact registration"
			}
			primary = registration
			if (rollback == registration) rollback = null
			cancellationFailures.remove(registration)
		}

		fun trackRollback(registration: ReaderTask6FactOnlyTimerRegistration) {
			if (primary == registration) {
				check(rollback == null)
				return
			}
			check(rollback == null || rollback == registration) {
				"Task 6 rollback timer ownership is bounded to one exact registration"
			}
			rollback = registration
			cancellationFailures.remove(registration)
		}

		fun promoteToPrimary(registration: ReaderTask6FactOnlyTimerRegistration) {
			check(contains(registration))
			primary = registration
			if (rollback == registration) rollback = null
			cancellationFailures.remove(registration)
		}

		fun recordCancellationFailure(
			registration: ReaderTask6FactOnlyTimerRegistration,
			disposition: ReaderReleaseOnlyCleanupDeadlineStatus
		) {
			check(contains(registration))
			cancellationFailures[registration] = disposition
		}

		fun clear(registration: ReaderTask6FactOnlyTimerRegistration) {
			if (primary == registration) primary = null
			if (rollback == registration) rollback = null
			cancellationFailures.remove(registration)
		}
	}

	private sealed interface MailboxEntry {
		data class Fact(
			val fact: ReaderTransitionFact,
			val trustedSemanticAuthority: ReaderReleaseOnlySemanticAuthority? = null,
			val trustedSemanticViolation: Boolean = false
		) : MailboxEntry
		data class SemanticCallbackDrain(
			val invocation: SemanticInvocation,
			val batch: SemanticCallbackBatch
		) : MailboxEntry
		data class SemanticCompletion(
			val invocation: SemanticInvocation,
			val outcome: SemanticCompletionOutcome
		) : MailboxEntry
		data class SemanticAuthorityRefresh(
			val authority: ReaderReleaseOnlySemanticAuthority
		) : MailboxEntry
	}

	private enum class SemanticCompletionOutcome {
		Accepted,
		RejectedBeforeMutation,
		ThrewBeforeMutation,
		RejectedAfterMutationStarted,
		ThrewAfterMutationStarted
	}

	private enum class SemanticInvocationCompletion {
		Pending,
		Accepted,
		RejectedWithoutCallback,
		ThrewWithoutCallback,
		RejectedAfterCallback,
		ThrewAfterCallback,
		RejectedAfterMutationStarted,
		ThrewAfterMutationStarted
	}

	private enum class SemanticInvocationDisposition {
		Active,
		ReceiptQueued,
		Rejected,
		Threw,
		Superseded,
		Terminal
	}

	private class SemanticCallbackBatch {
		var latestAuthoritativeReceipt: ReaderPresentationEventReceipt? = null
			private set
		var callbackCount: ReaderSaturatingCallbackCount = ReaderSaturatingCallbackCount.Zero
			private set

		fun record(receipt: ReaderPresentationEventReceipt) {
			latestAuthoritativeReceipt = receipt
			callbackCount = callbackCount.increment()
		}
	}

	private class SemanticInvocation(
		val transitionId: ReaderTransitionId
	) {
		var latestAuthoritativeReceipt: ReaderPresentationEventReceipt? = null
		var latestSemanticAuthority: ReaderReleaseOnlySemanticAuthority? = null
		var callbackCount: ReaderSaturatingCallbackCount = ReaderSaturatingCallbackCount.Zero
		var completion: SemanticInvocationCompletion = SemanticInvocationCompletion.Pending
		var disposition: SemanticInvocationDisposition = SemanticInvocationDisposition.Active
		var registration: ReaderSemanticCommandRegistration? = null
		var openCallbackBatch: SemanticCallbackBatch? = null
		var violationQueued: Boolean = false

		fun record(batch: SemanticCallbackBatch) {
			val receipt = batch.latestAuthoritativeReceipt ?: return
			latestAuthoritativeReceipt = receipt
			receipt.semanticReceipt?.binding?.let { binding ->
				latestSemanticAuthority = ReaderReleaseOnlySemanticAuthority(binding)
			}
			repeat(batch.callbackCount.boundedValue()) {
				callbackCount = callbackCount.increment()
			}
		}
	}

	// Linearizes callback, completion, and fact publication only. Reduction and port calls never
	// execute while this lock is held.
	private val mailboxLock = Any()
	private val mailbox = ArrayDeque<MailboxEntry>()
	private val mainHandler = Handler(Looper.getMainLooper())
	private val semanticReceiptConsumption = ReaderPresentationSemanticReceiptConsumption()
	private val classificationCounts = linkedMapOf<ReaderTransitionFactClassification, Int>()
	private val shadowPredictions = ArrayDeque<ReaderTransitionShadowPrediction>()
	private var advancing = false
	private var mainDrainScheduled = false
	private var advanceDepth = 0
	private var maxAdvanceDepth = 0
	private var activeTransitionsRegistered = 0
	private var registeredTransitionId: ReaderTransitionId? = null
	private var activeSemanticInvocation: SemanticInvocation? = null
	private var deadlineRecord: ActiveDeadlineRecord? = null
	private var deadlineSlot: DeadlineSlot? = null
	private val task6TimerOwnership = Task6TimerOwnership()
	private val task6AuthoritativeExpiries = mutableListOf<Task6AuthoritativeExpiry>()
	private var nextDeadlineSlotToken = 1L
	private var releaseOnlySink = false

	init {
		journal.resourceRegistrations().forEach(releaseLedger::register)
		journal.resourceKeys().forEach(releaseLedger::register)
	}

	fun enqueue(receipt: ReaderPresentationEventReceipt) {
		check(Looper.myLooper() == Looper.getMainLooper()) {
			"Reader transition receipts must be enqueued on the main thread"
		}
		when (receipt.origin) {
			ReaderPresentationEventOrigin.NonSemantic -> return
			ReaderPresentationEventOrigin.UnsolicitedFoliate -> {
				val relocation = receipt.event as? ReaderPresentationEvent.FoliateRelocated ?: return
				if (receipt.disposition == ReaderPresentationEventDisposition.Accepted) {
					enqueue(ReaderTransitionFact.FoliateDestinationCommitted(null, relocation.binding))
				}
				return
			}
			is ReaderPresentationEventOrigin.SemanticCommand -> Unit
		}
		when (val semantic = semanticReceiptConsumption.consume(receipt)) {
			is ReaderPresentationSemanticReceipt.Destination -> enqueue(
				ReaderTransitionFact.FoliateDestinationCommitted(semantic.transitionId, semantic.binding)
			)
			is ReaderPresentationSemanticReceipt.Settlement -> {
				val exactId = semantic.transitionId ?: return
				enqueue(
					ReaderTransitionFact.SettlementAcknowledged(
						exactId,
						semantic.binding,
						semantic.acknowledgement
					)
				)
			}
			null -> Unit
		}
	}

	fun enqueue(fact: ReaderTransitionFact) {
		check(ports.acceptsFact(fact)) {
			"Transition ports cannot accept this fact"
		}
		check(Looper.myLooper() == Looper.getMainLooper()) {
			"Reader transition facts must be enqueued on the main thread"
		}
		appendMailboxEntry(MailboxEntry.Fact(fact))
	}

	fun snapshot(): ReaderTransitionCoordinatorSnapshot {
		val releaseSnapshot = releaseLedger.snapshot()
		val mailboxSnapshot = synchronized(mailboxLock) {
			Triple(mailbox.size, advancing, maxAdvanceDepth)
		}
		return ReaderTransitionCoordinatorSnapshot(
			mode = mode,
			mailboxSize = mailboxSnapshot.first,
			advancing = mailboxSnapshot.second,
			maxAdvanceDepth = mailboxSnapshot.third,
			activeTransitionsRegistered = activeTransitionsRegistered,
			activeOperation = journal.active?.id?.operation,
			activePhase = journal.active?.phase?.kind,
			scheduledCallbackCount = (if (deadlineSlot == null) 0 else 1) + task6TimerOwnership.count,
			factClassifications = classificationCounts.toMap(),
			shadowPredictions = shadowPredictions.toList(),
			lastOutcome = journal.lastOutcome?.kind(),
			ownedResourceCount = releaseSnapshot.ownedCount,
			releaseCommandIssuedCount = releaseSnapshot.issuedCount,
			releasedResourceCount = releaseSnapshot.releasedCount,
			consumedSettlementCount = if (journal.active?.consumedSettlement == null) 0 else 1,
			releaseOnlySink = releaseOnlySink,
			releaseOnlySemanticAuthorityRetained =
				journal.releaseOnlyCleanup?.authoritativeSemanticDestination != null,
			releaseOnlyDeadlineStatus = journal.releaseOnlyCleanup?.deadlineStatus
		)
	}

	fun releaseStateOf(key: ReaderTransitionResourceKey): ReaderTransitionResourceState? =
		releaseLedger.stateOf(key)

	private fun appendMailboxEntry(entry: MailboxEntry) {
		val shouldDispatch = synchronized(mailboxLock) {
			mailbox.addLast(entry)
			markMainDrainScheduledLocked()
		}
		if (shouldDispatch) dispatchMainDrain()
	}

	private fun markMainDrainScheduledLocked(): Boolean {
		if (advancing || mainDrainScheduled) return false
		mainDrainScheduled = true
		return true
	}

	private fun dispatchMainDrain() {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			advance()
		} else {
			postMainDrain()
		}
	}

	private fun postMainDrain() {
		if (mainHandler.post(::advance)) return
		synchronized(mailboxLock) {
			mainDrainScheduled = false
		}
	}

	private fun advance() {
		check(Looper.myLooper() == Looper.getMainLooper()) {
			"Reader transition mailbox must drain on the main thread"
		}
		val admitted = synchronized(mailboxLock) {
			if (advancing) {
				false
			} else {
				advancing = true
				mainDrainScheduled = false
				advanceDepth += 1
				maxAdvanceDepth = maxOf(maxAdvanceDepth, advanceDepth)
				true
			}
		}
		if (!admitted) return
		try {
			while (true) {
				val entry = synchronized(mailboxLock) {
					if (mailbox.isEmpty()) {
						null
					} else {
						mailbox.removeFirst().also { removed ->
							if (
								removed is MailboxEntry.SemanticCallbackDrain &&
								removed.invocation.openCallbackBatch === removed.batch
							) {
								removed.invocation.openCallbackBatch = null
							}
						}
					}
				} ?: break
				if (entry is MailboxEntry.SemanticCallbackDrain) {
					processSemanticCallbackDrain(entry.invocation, entry.batch)
					continue
				}
				if (entry is MailboxEntry.SemanticCompletion) {
					processSemanticCompletion(entry.invocation, entry.outcome)
					continue
				}
				if (entry is MailboxEntry.SemanticAuthorityRefresh) {
					journal = journal.retainTrustedSemanticAuthority(entry.authority)
					continue
				}
				val factEntry = entry as MailboxEntry.Fact
				val fact = factEntry.fact
				accountAuthoritativeTask6Expiry(fact)
				val before = journal
				val classification = classify(fact, before)
				classificationCounts[classification] =
					classificationCounts.getOrElse(classification) { 0 } + 1
				val nowMillis = ports.clock.nowMillis()
				val registeredKey = fact.registeredResourceKeyOrNull()
				registeredKey?.let(releaseLedger::register)
				if (fact is ReaderTransitionFact.ResourceReleased) {
					fact.registration?.let(releaseLedger::confirmReleased)
						?: releaseLedger.confirmReleased(fact.key)
				}
				val reduction = when {
					factEntry.trustedSemanticViolation -> journal.reduceTrustedSemanticPortContractViolation(
						fact as ReaderTransitionFact.SemanticPortContractViolated,
						factEntry.trustedSemanticAuthority
					)
					releaseOnlySink -> paige.navic.reader.ReaderTransitionReduction(before, emptyList())
					else -> journal.reduce(fact, nowMillis)
				}

				// These assignments are the coordinator's publication barrier: callbacks may run
				// synchronously from either deadline registration or command issuance below.
				journal = reduction.state
				if (journal.releaseOnlyCleanup != null) {
					releaseOnlySink = true
				}
				persistActiveRegistration()
				val commandDispatchAllowed = reconcileDeadline(
					nowMillis,
					before,
					fact,
					classification
				)
				val predictedCommands = buildList {
					addAll(reduction.commands)
					if (releaseOnlySink && registeredKey != null) {
						when (val owner = registeredKey.ownerId) {
							is ReaderTransitionResourceOwnerId.TransitionOwned -> add(
								ReaderTransitionCommand.ReleaseResource(owner.transitionId, registeredKey)
							)
							is ReaderTransitionResourceOwnerId.AdoptedPredecessor -> Unit
						}
					}
				}
				recordShadowPrediction(fact, classification, before, predictedCommands)

				if (mode == ReaderTransitionMode.Active) {
					predictedCommands.forEach { predicted ->
						if (commandDispatchAllowed || predicted.pendingStageOrNull() == null) {
							accountCommand(predicted)?.let(::issueCommandWithPublicationAcknowledgement)
						}
					}
				}
				onObservation(
					ReaderTransitionCoordinatorObservation(
						ReaderTransitionCoordinatorObservationKind.FactProcessed,
						fact.kind()
					)
				)
				retireSemanticInvocationWhenAuthorityEnds()
			}
		} finally {
			val postDrain = synchronized(mailboxLock) {
				advanceDepth -= 1
				advancing = false
				if (mailbox.isNotEmpty() && !mainDrainScheduled) {
					mainDrainScheduled = true
					true
				} else {
					false
				}
			}
			if (postDrain) postMainDrain()
		}
	}

	private fun issueCommandWithPublicationAcknowledgement(command: ReaderTransitionCommand) {
		if (command is ReaderTransitionCommand.RequestSemanticSynchronization) {
			val semantic = ports.semanticCommand
			if (semantic != null) {
				val invocation = SemanticInvocation(command.transitionId)
				activeSemanticInvocation?.let { active ->
					retireSemanticInvocation(active, SemanticInvocationDisposition.Superseded)
				}
				activeSemanticInvocation = invocation
				val outcome = try {
					when (
						semantic.synchronize(
							command,
							onRegistration = { registration ->
								check(invocation.registration == null) {
									"Semantic invocation registration must be exact and singular"
								}
								invocation.registration = registration
							}
						) { receipt ->
							enqueueSemanticCallback(invocation, receipt)
						}
					) {
						ReaderSemanticCommandResult.Accepted -> SemanticCompletionOutcome.Accepted
						is ReaderSemanticCommandResult.RejectedBeforeMutation ->
							SemanticCompletionOutcome.RejectedBeforeMutation
						ReaderSemanticCommandResult.ThrewBeforeMutation ->
							SemanticCompletionOutcome.ThrewBeforeMutation
						ReaderSemanticCommandResult.RejectedAfterMutationStarted ->
							SemanticCompletionOutcome.RejectedAfterMutationStarted
						ReaderSemanticCommandResult.ThrewAfterMutationStarted ->
							SemanticCompletionOutcome.ThrewAfterMutationStarted
					}
				} catch (_: Throwable) {
					SemanticCompletionOutcome.ThrewBeforeMutation
				}
				enqueueSemanticCompletion(invocation, outcome)
				return
			}
		}
		val publication = ports.ownerAndInputPublication
		val result = try {
			when (command) {
				is ReaderTransitionCommand.CommitOwnerAndInputLease -> publication?.publish(command)
				is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease -> publication?.publish(command)
				else -> null
			}
		} catch (_: Throwable) {
			enqueueCommandThrow(command)
			return
		}
		if (result == null) {
			try {
				ports.issue(command, ::enqueue)
			} catch (throwable: Throwable) {
				if (command.pendingStageOrNull() == null) throw throwable
				enqueueCommandThrow(command)
			}
		} else {
			enqueue(result.toTransitionFact())
		}
	}

	private fun enqueueSemanticCallback(
		invocation: SemanticInvocation,
		receipt: ReaderPresentationEventReceipt
	) {
		val shouldDispatch = synchronized(mailboxLock) {
			val batch = invocation.openCallbackBatch ?: SemanticCallbackBatch().also {
				invocation.openCallbackBatch = it
				mailbox.addLast(MailboxEntry.SemanticCallbackDrain(invocation, it))
			}
			batch.record(receipt)
			markMainDrainScheduledLocked()
		}
		if (shouldDispatch) dispatchMainDrain()
	}

	private fun enqueueSemanticCompletion(
		invocation: SemanticInvocation,
		outcome: SemanticCompletionOutcome
	) {
		val shouldDispatch = synchronized(mailboxLock) {
			// Seal callbacks observed before return; a later callback receives a batch behind completion.
			invocation.openCallbackBatch = null
			mailbox.addLast(MailboxEntry.SemanticCompletion(invocation, outcome))
			markMainDrainScheduledLocked()
		}
		if (shouldDispatch) dispatchMainDrain()
	}

	private fun processSemanticCallbackDrain(
		invocation: SemanticInvocation,
		batch: SemanticCallbackBatch
	) {
		check(Looper.myLooper() == Looper.getMainLooper())
		invocation.record(batch)
		if (invocation.latestAuthoritativeReceipt == null) return
		when (invocation.disposition) {
			SemanticInvocationDisposition.Superseded,
			SemanticInvocationDisposition.Terminal -> {
				enqueueLatestSemanticReceipt(invocation)
				enqueueSemanticViolation(
					invocation,
					ReaderSemanticPortContractViolationReason.CallbackAfterTerminalDisposition
				)
				retireSemanticInvocation(invocation, invocation.disposition)
			}
			SemanticInvocationDisposition.Rejected,
			SemanticInvocationDisposition.Threw -> {
				enqueueLatestSemanticReceipt(invocation)
				enqueueSemanticViolation(
					invocation,
					ReaderSemanticPortContractViolationReason.RejectedThenLateCallback
				)
				retireSemanticInvocation(invocation, invocation.disposition)
			}
			SemanticInvocationDisposition.ReceiptQueued -> {
				enqueueLatestSemanticReceipt(invocation)
				enqueueSemanticViolation(
					invocation,
					ReaderSemanticPortContractViolationReason.DuplicateCallback
				)
				retireSemanticInvocation(invocation, SemanticInvocationDisposition.ReceiptQueued)
			}
			SemanticInvocationDisposition.Active -> when (invocation.completion) {
				SemanticInvocationCompletion.Pending -> Unit
				SemanticInvocationCompletion.Accepted -> {
					enqueueLatestSemanticReceipt(invocation)
					if (invocation.callbackCount != ReaderSaturatingCallbackCount.One) {
						enqueueSemanticViolation(
							invocation,
							ReaderSemanticPortContractViolationReason.DuplicateCallback
						)
					}
					retireSemanticInvocation(
						invocation,
						SemanticInvocationDisposition.ReceiptQueued
					)
				}
				SemanticInvocationCompletion.RejectedWithoutCallback,
				SemanticInvocationCompletion.RejectedAfterCallback,
				SemanticInvocationCompletion.RejectedAfterMutationStarted -> {
					enqueueLatestSemanticReceipt(invocation)
					enqueueSemanticViolation(
						invocation,
						ReaderSemanticPortContractViolationReason.RejectedThenLateCallback
					)
					retireSemanticInvocation(invocation, SemanticInvocationDisposition.Rejected)
				}
				SemanticInvocationCompletion.ThrewWithoutCallback,
				SemanticInvocationCompletion.ThrewAfterCallback,
				SemanticInvocationCompletion.ThrewAfterMutationStarted -> {
					enqueueLatestSemanticReceipt(invocation)
					enqueueSemanticViolation(
						invocation,
						ReaderSemanticPortContractViolationReason.RejectedThenLateCallback
					)
					retireSemanticInvocation(invocation, SemanticInvocationDisposition.Threw)
				}
			}
		}
	}

	private fun processSemanticCompletion(
		invocation: SemanticInvocation,
		outcome: SemanticCompletionOutcome
	) {
		check(Looper.myLooper() == Looper.getMainLooper())
		if (invocation.completion != SemanticInvocationCompletion.Pending) return
		when (outcome) {
			SemanticCompletionOutcome.Accepted -> {
				invocation.completion = SemanticInvocationCompletion.Accepted
				if (invocation.callbackCount != ReaderSaturatingCallbackCount.Zero) {
					enqueueLatestSemanticReceipt(invocation)
					if (invocation.callbackCount != ReaderSaturatingCallbackCount.One) {
						enqueueSemanticViolation(
							invocation,
							ReaderSemanticPortContractViolationReason.DuplicateCallback
						)
					}
					retireSemanticInvocation(
						invocation,
						SemanticInvocationDisposition.ReceiptQueued
					)
				}
			}
			SemanticCompletionOutcome.RejectedBeforeMutation -> {
				if (invocation.callbackCount == ReaderSaturatingCallbackCount.Zero) {
					invocation.completion = SemanticInvocationCompletion.RejectedWithoutCallback
					enqueueSemanticCommandRejection(
						invocation.transitionId,
						ReaderTransitionCommandRejectionReason.SemanticExecutionRejected
					)
				} else {
					invocation.completion = SemanticInvocationCompletion.RejectedAfterCallback
					enqueueLatestSemanticReceipt(invocation)
					enqueueSemanticViolation(
						invocation,
						if (invocation.callbackCount == ReaderSaturatingCallbackCount.One) {
							ReaderSemanticPortContractViolationReason.CallbackThenRejected
						} else {
							ReaderSemanticPortContractViolationReason.DuplicateCallbackThenRejected
						}
					)
				}
				retireSemanticInvocation(invocation, SemanticInvocationDisposition.Rejected)
			}
			SemanticCompletionOutcome.ThrewBeforeMutation -> {
				if (invocation.callbackCount == ReaderSaturatingCallbackCount.Zero) {
					invocation.completion = SemanticInvocationCompletion.ThrewWithoutCallback
					enqueueSemanticCommandRejection(
						invocation.transitionId,
						ReaderTransitionCommandRejectionReason.CommandThrew
					)
				} else {
					invocation.completion = SemanticInvocationCompletion.ThrewAfterCallback
					enqueueLatestSemanticReceipt(invocation)
					enqueueSemanticViolation(
						invocation,
						if (invocation.callbackCount == ReaderSaturatingCallbackCount.One) {
							ReaderSemanticPortContractViolationReason.CallbackThenThrew
						} else {
							ReaderSemanticPortContractViolationReason.DuplicateCallbackThenThrew
						}
					)
				}
				retireSemanticInvocation(invocation, SemanticInvocationDisposition.Threw)
			}
			SemanticCompletionOutcome.RejectedAfterMutationStarted -> {
				invocation.completion = SemanticInvocationCompletion.RejectedAfterMutationStarted
				if (invocation.callbackCount == ReaderSaturatingCallbackCount.Zero) {
					enqueueSemanticViolation(
						invocation,
						ReaderSemanticPortContractViolationReason.RejectedAfterMutationStarted
					)
				} else {
					enqueueLatestSemanticReceipt(invocation)
					enqueueSemanticViolation(
						invocation,
						if (invocation.callbackCount == ReaderSaturatingCallbackCount.One) {
							ReaderSemanticPortContractViolationReason.CallbackThenRejected
						} else {
							ReaderSemanticPortContractViolationReason.DuplicateCallbackThenRejected
						}
					)
				}
				retireSemanticInvocation(invocation, SemanticInvocationDisposition.Rejected)
			}
			SemanticCompletionOutcome.ThrewAfterMutationStarted -> {
				invocation.completion = SemanticInvocationCompletion.ThrewAfterMutationStarted
				if (invocation.callbackCount == ReaderSaturatingCallbackCount.Zero) {
					enqueueSemanticViolation(
						invocation,
						ReaderSemanticPortContractViolationReason.ThrowAfterMutationStarted
					)
				} else {
					enqueueLatestSemanticReceipt(invocation)
					enqueueSemanticViolation(
						invocation,
						if (invocation.callbackCount == ReaderSaturatingCallbackCount.One) {
							ReaderSemanticPortContractViolationReason.CallbackThenThrew
						} else {
							ReaderSemanticPortContractViolationReason.DuplicateCallbackThenThrew
						}
					)
				}
				retireSemanticInvocation(invocation, SemanticInvocationDisposition.Threw)
			}
		}
	}

	private fun enqueueLatestSemanticReceipt(invocation: SemanticInvocation) {
		val receipt = invocation.latestAuthoritativeReceipt ?: return
		invocation.latestAuthoritativeReceipt = null
		enqueue(receipt)
	}

	private fun enqueueSemanticViolation(
		invocation: SemanticInvocation,
		reason: ReaderSemanticPortContractViolationReason
	) {
		if (invocation.violationQueued) {
			invocation.latestSemanticAuthority?.let { authority ->
				appendMailboxEntry(MailboxEntry.SemanticAuthorityRefresh(authority))
			}
			return
		}
		invocation.violationQueued = true
		val fact = ReaderTransitionFact.SemanticPortContractViolated(
			transitionId = invocation.transitionId,
			reason = reason,
			callbackCount = invocation.callbackCount
		)
		check(ports.acceptsFact(fact)) {
			"Transition ports cannot accept semantic contract violations"
		}
		appendMailboxEntry(
			MailboxEntry.Fact(
				fact = fact,
				trustedSemanticAuthority = invocation.latestSemanticAuthority,
				trustedSemanticViolation = true
			)
		)
	}

	private fun enqueueSemanticCommandRejection(
		transitionId: ReaderTransitionId,
		reason: ReaderTransitionCommandRejectionReason
	) {
		enqueue(
			ReaderTransitionFact.CommandRejected(
				transitionId,
				ReaderTransitionCommandStage.SemanticSynchronization,
				reason
			)
		)
	}

	private fun retireSemanticInvocationWhenAuthorityEnds() {
		val invocation = activeSemanticInvocation ?: return
		val current = journal.active
		val disposition = when {
			releaseOnlySink || current == null -> SemanticInvocationDisposition.Terminal
			current.id != invocation.transitionId -> SemanticInvocationDisposition.Superseded
			ReaderTransitionCommandStage.SemanticSynchronization !in current.pendingCommandStages ->
				SemanticInvocationDisposition.Terminal
			else -> return
		}
		retireSemanticInvocation(invocation, disposition)
	}

	private fun retireSemanticInvocation(
		invocation: SemanticInvocation,
		disposition: SemanticInvocationDisposition
	) {
		if (invocation.disposition == SemanticInvocationDisposition.Active) {
			invocation.disposition = disposition
		}
		invocation.registration?.retire()
		invocation.registration = null
		if (activeSemanticInvocation === invocation) activeSemanticInvocation = null
	}

	private fun enqueueCommandThrow(command: ReaderTransitionCommand) {
		val stage = command.pendingStageOrNull() ?: return
		enqueue(
			ReaderTransitionFact.CommandRejected(
				requireNotNull(command.transitionId),
				stage,
				ReaderTransitionCommandRejectionReason.CommandThrew
			)
		)
	}

	private fun accountCommand(command: ReaderTransitionCommand): ReaderTransitionCommand? =
		if (command is ReaderTransitionCommand.ReleaseResource) {
			if (command.registration == null) {
				releaseLedger.register(command.key)
			} else {
				releaseLedger.register(command.registration)
			}
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
	): Boolean {
		if (activationState() == ReaderSessionActivationState.Activated) {
			return reconcileTask6FactOnlyTimer(nowMillis, before, fact, classification)
		}
		val active = journal.active
		if (active == null || active.isAwaitingRetainedPublication()) {
			cancelDeadlineSlot()
			deadlineRecord = null
			return true
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
		if (deadlineSlot?.key == nextKey && deadlineSlot?.atMillis == atMillis) return true

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
		return true
	}

	private fun reconcileTask6FactOnlyTimer(
		nowMillis: Long,
		before: ReaderTransitionJournal,
		fact: ReaderTransitionFact,
		classification: ReaderTransitionFactClassification
	): Boolean {
		cancelDeadlineSlot()
		deadlineRecord = null
		val port = ports.task6FactOnlyTimer
		val active = journal.active
		if (active == null || active.isAwaitingRetainedPublication()) {
			cancelTask6TimersForTerminalState(port)
			return true
		}
		val rollback = task6TimerOwnership.rollback
		if (rollback != null) {
			val disposition = cancelTrackedTask6Timer(port, rollback)
			if (disposition != null) {
				journal = journal.copy(
					active = active.copy(
						pendingCommandStages = setOf(ReaderTransitionCommandStage.TimerBinding)
					)
				)
				enqueueTimerBindingRejection(
					active.id,
					ReaderTransitionCommandRejectionReason.TimerOwnershipConflict
				)
				return false
			}
		}

		var existing = task6TimerOwnership.primary
		if (
			existing != null &&
			task6TimerOwnership.failure(existing) != null &&
			existing.transitionId != active.id
		) {
			val disposition = cancelTrackedTask6Timer(port, existing)
			if (disposition == null) {
				existing = null
			} else {
				journal = journal.copy(
					active = active.copy(
						pendingCommandStages = setOf(ReaderTransitionCommandStage.TimerBinding)
					)
				)
				enqueueTimerBindingRejection(
					active.id,
					ReaderTransitionCommandRejectionReason.TimerOwnershipConflict
				)
				return false
			}
		}

		if (synchronized(mailboxLock) {
				mailbox.any { entry ->
					val queued = (entry as? MailboxEntry.Fact)?.fact
					queued is ReaderTransitionFact.CommandRejected &&
						queued.transitionId == active.id &&
						queued.stage == ReaderTransitionCommandStage.TimerBinding
				}
			}) return false

		if (existing?.transitionId == active.id) {
			if (
				existing.permitsMatchingProgressRearm &&
				fact.isMatchingProgress(classification, before, journal)
			) {
				val pendingPhysicalStage = active.pendingCommandStages.singleOrNull()
				journal = journal.copy(
					active = active.copy(
						pendingCommandStages = setOf(ReaderTransitionCommandStage.TimerBinding)
					)
				)
				val result = try {
					port?.matchingProgress(existing, nowMillis)
				} catch (_: Throwable) {
					enqueueTimerBindingRejection(
						active.id,
						ReaderTransitionCommandRejectionReason.CommandThrew
					)
					return false
				}
				if (result != ReaderPortCommandResult.Accepted) {
					enqueueTimerBindingRejection(
						active.id,
						ReaderTransitionCommandRejectionReason.TimerBindingRejected
					)
					return false
				}
				if (hasPendingAuthoritativeTask6Expiry(existing)) return false
				restorePhysicalCommandStage(active.id, pendingPhysicalStage)
			}
			return true
		}

		val pendingPhysicalStage = active.pendingCommandStages.singleOrNull()
		journal = journal.copy(
			active = active.copy(
				pendingCommandStages = setOf(ReaderTransitionCommandStage.TimerBinding)
			)
		)
		if (port == null) {
			enqueueTimerBindingRejection(
				active.id,
				ReaderTransitionCommandRejectionReason.TimerBindingRejected
			)
			return false
		}

		var bindingInProgress = true
		var boundRegistration: ReaderTask6FactOnlyTimerRegistration? = null
		var preReturnExpiry: ReaderTransitionFact.DeadlineExpired? = null
		val successor = try {
			port.bindBeforeWork(active.id) { expired ->
				val registration = boundRegistration
				when {
					registration != null && isTrackedTask6TimerRegistration(registration) ->
						enqueueAuthoritativeTask6Expiry(expired, registration)
					bindingInProgress -> preReturnExpiry = expired
				}
			}
		} catch (_: Throwable) {
			bindingInProgress = false
			enqueueTimerBindingRejection(
				active.id,
				ReaderTransitionCommandRejectionReason.CommandThrew
			)
			return false
		}
		boundRegistration = successor
		bindingInProgress = false
		if (successor == null) {
			enqueueTimerBindingRejection(
				active.id,
				ReaderTransitionCommandRejectionReason.TimerBindingRejected
			)
			return false
		}
		val observedExpiry = preReturnExpiry?.takeIf { it.transitionId == successor.transitionId }
		if (successor.transitionId != active.id) {
			task6TimerOwnership.trackRollback(successor)
			observedExpiry?.let { enqueueAuthoritativeTask6Expiry(it, successor) }
			cancelTrackedTask6Timer(port, successor)
			enqueueTimerBindingRejection(
				active.id,
				ReaderTransitionCommandRejectionReason.TimerOwnershipConflict
			)
			return false
		}
		if (existing != null) {
			task6TimerOwnership.trackRollback(successor)
			observedExpiry?.let { enqueueAuthoritativeTask6Expiry(it, successor) }
			val oldDisposition = cancelTrackedTask6Timer(port, existing)
			if (oldDisposition != null) {
				cancelTrackedTask6Timer(port, successor)
				enqueueTimerBindingRejection(
					active.id,
					ReaderTransitionCommandRejectionReason.TimerOwnershipConflict
				)
				return false
			}
			task6TimerOwnership.promoteToPrimary(successor)
		} else {
			task6TimerOwnership.trackPrimary(successor)
			observedExpiry?.let { enqueueAuthoritativeTask6Expiry(it, successor) }
		}
		if (hasPendingAuthoritativeTask6Expiry(successor)) return false
		restorePhysicalCommandStage(active.id, pendingPhysicalStage)
		return true
	}

	private fun restorePhysicalCommandStage(
		transitionId: ReaderTransitionId,
		stage: ReaderTransitionCommandStage?
	) {
		journal.active?.takeIf {
			it.id == transitionId &&
				it.pendingCommandStages == setOf(ReaderTransitionCommandStage.TimerBinding)
		}?.let { current ->
			journal = journal.copy(
				active = current.copy(pendingCommandStages = setOfNotNull(stage))
			)
		}
	}

	private fun attemptTask6TimerCancellation(
		port: ReaderTask6FactOnlyTimerPort?,
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderReleaseOnlyCleanupDeadlineStatus? = try {
		when (port?.cancel(registration)) {
			ReaderPortCommandResult.Accepted -> null
			is ReaderPortCommandResult.Rejected,
			null -> ReaderReleaseOnlyCleanupDeadlineStatus.CancellationRejected
		}
	} catch (_: Throwable) {
		ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew
	}

	private fun cancelTrackedTask6Timer(
		port: ReaderTask6FactOnlyTimerPort?,
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderReleaseOnlyCleanupDeadlineStatus? {
		check(task6TimerOwnership.contains(registration))
		val disposition = attemptTask6TimerCancellation(port, registration)
		if (disposition == null) {
			task6TimerOwnership.clear(registration)
		} else {
			task6TimerOwnership.recordCancellationFailure(registration, disposition)
		}
		return disposition
	}

	private fun cancelTask6TimersForTerminalState(port: ReaderTask6FactOnlyTimerPort?) {
		val permanent = journal.releaseOnlyCleanup != null
		task6TimerOwnership.registrations.toList().forEach { registration ->
			if (!permanent || task6TimerOwnership.failure(registration) == null) {
				cancelTrackedTask6Timer(port, registration)
			}
		}
		recordTask6TimerCancellationDisposition()
	}

	private fun recordTask6TimerCancellationDisposition() {
		val cleanup = journal.releaseOnlyCleanup ?: return
		val failures = task6TimerOwnership.failures
		val disposition = when {
			ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew in failures ->
				ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew
			ReaderReleaseOnlyCleanupDeadlineStatus.CancellationRejected in failures ->
				ReaderReleaseOnlyCleanupDeadlineStatus.CancellationRejected
			task6TimerOwnership.count == 0 &&
				cleanup.deadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Armed ->
				ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting
			else -> return
		}
		journal = journal.copy(
			releaseOnlyCleanup = cleanup.copy(deadlineStatus = disposition)
		)
	}

	private fun isTrackedTask6TimerRegistration(
		registration: ReaderTask6FactOnlyTimerRegistration
	): Boolean = task6TimerOwnership.contains(registration)

	private fun hasPendingAuthoritativeTask6Expiry(
		registration: ReaderTask6FactOnlyTimerRegistration
	): Boolean = task6AuthoritativeExpiries.any { it.registration == registration }

	private fun enqueueAuthoritativeTask6Expiry(
		fact: ReaderTransitionFact.DeadlineExpired,
		registration: ReaderTask6FactOnlyTimerRegistration
	) {
		require(fact.transitionId == registration.transitionId)
		if (hasPendingAuthoritativeTask6Expiry(registration)) return
		task6AuthoritativeExpiries += Task6AuthoritativeExpiry(fact, registration)
		enqueue(fact)
	}

	private fun accountAuthoritativeTask6Expiry(fact: ReaderTransitionFact) {
		val expiry = fact as? ReaderTransitionFact.DeadlineExpired ?: return
		val evidenceIndex = task6AuthoritativeExpiries.indexOfFirst { it.fact === expiry }
		if (evidenceIndex < 0) return
		val registration = task6AuthoritativeExpiries.removeAt(evidenceIndex).registration
		task6TimerOwnership.clear(registration)
	}

	private fun enqueueTimerBindingRejection(
		transitionId: ReaderTransitionId,
		reason: ReaderTransitionCommandRejectionReason
	) {
		enqueue(
			ReaderTransitionFact.CommandRejected(
				transitionId,
				ReaderTransitionCommandStage.TimerBinding,
				reason
			)
		)
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

private fun ReaderActiveTransition.isAwaitingRetainedPublication(): Boolean =
	pendingPublicationIdentity != null &&
		frameTarget == null &&
		preparedFrameOwner == null &&
		phase.contract.awaitedProofs == setOf(
			paige.navic.reader.ReaderTransitionProofKind.OwnerAndInputPublicationAcknowledgement
		)

private fun ReaderOwnerAndInputPublicationResult.toTransitionFact(): ReaderTransitionFact = when (this) {
	is ReaderOwnerAndInputPublicationResult.Applied -> ReaderTransitionFact.OwnerAndInputPublicationApplied(
		transitionId,
		subject,
		publishedOwner,
		publishedBinding,
		finalPhysicalInputLease,
		publicationIdentity
	)
	is ReaderOwnerAndInputPublicationResult.Rejected -> ReaderTransitionFact.OwnerAndInputPublicationRejected(
		transitionId,
		subject,
		publicationIdentity,
		reason
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

private fun ReaderTransitionJournal.resourceRegistrations(): Set<ReaderTransitionResourceRegistration> = buildSet {
	when (val presentation = committed) {
		is ReaderCommittedPresentation.Initial -> when (val origin = presentation.origin) {
			is ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor -> add(origin.resource)
			is ReaderInitialCommittedPresentationOrigin.Neutral -> Unit
		}
		is ReaderCommittedPresentation.Transition -> add(presentation.committed.resourceRegistration)
	}
	active?.let { current ->
		current.predecessorResourceRegistration?.let(::add)
		current.pendingFrameTargetRegistration?.let(::add)
		current.successorResourceRegistration?.let(::add)
	}
}

private fun ReaderTransitionJournal.resourceKeys(): Set<ReaderTransitionResourceKey> = buildSet {
	when (val presentation = committed) {
		is ReaderCommittedPresentation.Initial -> when (val origin = presentation.origin) {
			is ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor -> add(origin.resource.key)
			is ReaderInitialCommittedPresentationOrigin.Neutral -> Unit
		}
		is ReaderCommittedPresentation.Transition -> add(presentation.committed.resourceKey)
	}
	active?.let { current ->
		current.predecessorResourceKey?.let(::add)
		addAll(current.ownedResourceKeys)
		current.admittedDeckKey?.let(::add)
		current.pendingPreparedDeckKey?.let(::add)
		current.pendingFrameTargetRegistration?.key?.let(::add)
		current.frameTarget?.resource?.key?.let(::add)
		current.successorResourceKey?.let(::add)
	}
}

private fun ReaderTransitionFact.registeredResourceKeyOrNull(): ReaderTransitionResourceKey? = when (this) {
	is ReaderTransitionFact.ResourceObserved -> key
	is ReaderTransitionFact.DeckReserved -> key
	is ReaderTransitionFact.DeckOwned -> key
	is ReaderTransitionFact.DeckPrepared -> key
	is ReaderTransitionFact.DeckRejected -> key
	is ReaderTransitionFact.FrameTargetPrepared -> target.resource.key
	is ReaderTransitionFact.FrameTargetPreparationRejected -> resource.key
	is ReaderTransitionFact.PreparedFrame -> resource.key
	is ReaderTransitionFact.CoverPostDraw -> resourceKey
	is ReaderTransitionFact.WebViewExposure -> resourceKey
	is ReaderTransitionFact.Intent,
	is ReaderTransitionFact.FoliateDestinationCommitted,
	is ReaderTransitionFact.SettlementAcknowledged,
	is ReaderTransitionFact.MaterialBindingAllocated,
	is ReaderTransitionFact.OwnerAndInputPublicationApplied,
	is ReaderTransitionFact.OwnerAndInputPublicationRejected,
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
	is ReaderTransitionFact.CommandRejected,
	is ReaderTransitionFact.SemanticPortContractViolated,
	is ReaderTransitionFact.Retry,
	is ReaderTransitionFact.PublicationReplaced,
	is ReaderTransitionFact.PublicationClosed -> null
}

internal fun ReaderTransitionFact.isTask4CoordinatorFact(): Boolean = when (this) {
	is ReaderTransitionFact.MaterialBindingAllocated,
	is ReaderTransitionFact.RasterProgress,
	is ReaderTransitionFact.RasterProven,
	is ReaderTransitionFact.RasterDeferred,
	is ReaderTransitionFact.RasterFailed,
	is ReaderTransitionFact.RendererCapacityAvailable,
	is ReaderTransitionFact.RendererGenerationReady,
	is ReaderTransitionFact.ResourceLost,
	is ReaderTransitionFact.DeadlineExpired,
	is ReaderTransitionFact.CommandRejected,
	is ReaderTransitionFact.Retry,
	is ReaderTransitionFact.PublicationReplaced,
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
	is ReaderTransitionFact.SemanticPortContractViolated,
	is ReaderTransitionFact.FoliateDestinationCommitted,
	is ReaderTransitionFact.SettlementAcknowledged,
	is ReaderTransitionFact.ViewportProfileReplaced,
	is ReaderTransitionFact.HostAvailable,
	is ReaderTransitionFact.PaginationProfileReady,
	is ReaderTransitionFact.FrameTargetPrepared,
	is ReaderTransitionFact.FrameTargetPreparationRejected,
	is ReaderTransitionFact.PreparedFrame,
	is ReaderTransitionFact.OwnerAndInputPublicationApplied,
	is ReaderTransitionFact.OwnerAndInputPublicationRejected,
	is ReaderTransitionFact.CoverPostDraw,
	is ReaderTransitionFact.WebViewExposure,
	is ReaderTransitionFact.VisibilityChanged -> false
}

private fun ReaderTransitionResourceKey.isTask4ResourceKeyFor(
	factTransitionId: ReaderTransitionId?,
	vararg allowedKinds: ReaderTransitionResourceKind
): Boolean {
	if (owningTransitionIdOrNull != factTransitionId) return false
	return if (allowedKinds.isEmpty()) {
		kind == ReaderTransitionResourceKind.Deck || kind == ReaderTransitionResourceKind.Raster
	} else {
		kind in allowedKinds
	}
}

private fun ReaderExpectedPresentationBinding.matchesSemanticReceipt(
	binding: paige.navic.reader.ReaderPresentationBinding
): Boolean = when (this) {
	is ReaderExpectedPresentationBinding.Exact -> this.binding == binding
	is ReaderExpectedPresentationBinding.SemanticSuccessor ->
		binding != predecessor &&
			binding.foliateSessionId == predecessor.foliateSessionId &&
			binding.publicationGeneration == predecessor.publicationGeneration
}

private fun ReaderSaturatingCallbackCount.boundedValue(): Int = when (this) {
	ReaderSaturatingCallbackCount.Zero -> 0
	ReaderSaturatingCallbackCount.One -> 1
	ReaderSaturatingCallbackCount.Two -> 2
	ReaderSaturatingCallbackCount.ThreeOrMore -> 3
}

private fun Long.saturatingAdd(increment: Long): Long =
	if (this > Long.MAX_VALUE - increment) Long.MAX_VALUE else this + increment

private fun classify(
	fact: ReaderTransitionFact,
	journal: ReaderTransitionJournal
): ReaderTransitionFactClassification {
	if (fact is ReaderTransitionFact.SettlementAcknowledged) {
		val active = journal.active ?: return ReaderTransitionFactClassification.StaleTransition
		return if (
			fact.transitionId == active.id &&
			active.id.operation == ReaderTransitionOperation.CurlClaimAndSettlement &&
			active.id.expectedBinding.matchesSemanticReceipt(fact.binding) &&
			active.resolvedSuccessorBinding?.let { it != fact.binding } != true &&
			active.consumedSettlement == null &&
			paige.navic.reader.ReaderTransitionProofKind.SettlementAcknowledgement in
				active.phase.contract.awaitedProofs
		) {
			ReaderTransitionFactClassification.CurrentTransition
		} else {
			ReaderTransitionFactClassification.StaleTransition
		}
	}
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
	is ReaderTransitionFact.MaterialBindingAllocated -> ReaderTransitionFactKind.MaterialBindingAllocated
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
	is ReaderTransitionFact.FrameTargetPrepared -> ReaderTransitionFactKind.FrameTargetPrepared
	is ReaderTransitionFact.FrameTargetPreparationRejected -> ReaderTransitionFactKind.FrameTargetPreparationRejected
	is ReaderTransitionFact.PreparedFrame -> ReaderTransitionFactKind.PreparedFrame
	is ReaderTransitionFact.OwnerAndInputPublicationApplied -> ReaderTransitionFactKind.OwnerAndInputPublicationApplied
	is ReaderTransitionFact.OwnerAndInputPublicationRejected -> ReaderTransitionFactKind.OwnerAndInputPublicationRejected
	is ReaderTransitionFact.CoverPostDraw -> ReaderTransitionFactKind.CoverPostDraw
	is ReaderTransitionFact.WebViewExposure -> ReaderTransitionFactKind.WebViewExposure
	is ReaderTransitionFact.VisibilityChanged -> ReaderTransitionFactKind.VisibilityChanged
	is ReaderTransitionFact.ResourceLost -> ReaderTransitionFactKind.ResourceLost
	is ReaderTransitionFact.DeadlineExpired -> ReaderTransitionFactKind.DeadlineExpired
	is ReaderTransitionFact.CommandRejected -> ReaderTransitionFactKind.CommandRejected
	is ReaderTransitionFact.SemanticPortContractViolated ->
		ReaderTransitionFactKind.SemanticPortContractViolated
	is ReaderTransitionFact.Retry -> ReaderTransitionFactKind.Retry
	is ReaderTransitionFact.PublicationReplaced -> ReaderTransitionFactKind.PublicationReplaced
	is ReaderTransitionFact.PublicationClosed -> ReaderTransitionFactKind.PublicationClosed
}

private fun ReaderTransitionCommand.kind(): ReaderTransitionCommandKind = when (this) {
	is ReaderTransitionCommand.RequestSemanticSynchronization ->
		ReaderTransitionCommandKind.RequestSemanticSynchronization
	is ReaderTransitionCommand.AllocateMaterialBinding -> ReaderTransitionCommandKind.AllocateMaterialBinding
	is ReaderTransitionCommand.RequestRasterPreparation -> ReaderTransitionCommandKind.RequestRasterPreparation
	is ReaderTransitionCommand.ReserveDeck -> ReaderTransitionCommandKind.ReserveDeck
	is ReaderTransitionCommand.PrepareFrameTarget -> ReaderTransitionCommandKind.PrepareFrameTarget
	is ReaderTransitionCommand.RequestFramePresentation -> ReaderTransitionCommandKind.RequestFramePresentation
	is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease ->
		ReaderTransitionCommandKind.PublishRetainedOwnerAndInputLease
	is ReaderTransitionCommand.CommitOwnerAndInputLease -> ReaderTransitionCommandKind.CommitOwnerAndInputLease
	is ReaderTransitionCommand.ReleaseResource -> ReaderTransitionCommandKind.ReleaseResource
	is ReaderTransitionCommand.CancelOwnedWork -> ReaderTransitionCommandKind.CancelOwnedWork
}

private fun ReaderTransitionOutcome.kind(): ReaderTransitionOutcomeKind = when (this) {
	is ReaderTransitionOutcome.Succeeded -> ReaderTransitionOutcomeKind.Succeeded
	is ReaderTransitionOutcome.Failed -> ReaderTransitionOutcomeKind.Failed
	is ReaderTransitionOutcome.Cancelled -> ReaderTransitionOutcomeKind.Cancelled
	is ReaderTransitionOutcome.Deferred -> ReaderTransitionOutcomeKind.Deferred
}
