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
import paige.navic.reader.ReaderReleaseLedgerAdmissionRejectionReason
import paige.navic.reader.ReaderReleaseOnlyCancellationStatus
import paige.navic.reader.ReaderReleaseOnlyCleanupDeadlineStatus
import paige.navic.reader.ReaderReleaseOnlyCleanupKey
import paige.navic.reader.ReaderReleaseOnlySemanticAuthority
import paige.navic.reader.ReaderResourceReleaseIssuer
import paige.navic.reader.ReaderReleaseCommandIdentity
import paige.navic.reader.ReaderReleaseLedgerCleanupStatus
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
import paige.navic.reader.markReleaseOnlyCleanupDeadlineElapsed
import paige.navic.reader.markReleaseOnlyCleanupProcessClosed
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
	RejectedNoEffect,
	AmbiguousFailure,
	Released,
	TimedOutUnreleased,
	ProcessClosedUnreleased
}

internal enum class ReaderReleaseFailureTombstoneReason {
	RejectedNoEffectTimedOut,
	AmbiguousFailureTimedOut,
	RejectedNoEffectProcessClosed,
	AmbiguousFailureProcessClosed
}

internal data class ReaderReleaseFailureTombstone(
	val cleanupKey: paige.navic.reader.ReaderReleaseOnlyCleanupKey,
	val identity: paige.navic.reader.ReaderReleaseCommandIdentity,
	val reason: ReaderReleaseFailureTombstoneReason
) {
	override fun hashCode(): Int = 0x52465453
	override fun toString(): String = "ReaderReleaseFailureTombstone(<redacted>)"
}

internal data class ReaderReleaseFailureFenceSnapshot(
	val tombstones: Set<ReaderReleaseFailureTombstone>
) {
	init { require(tombstones.size <= 32) }
	override fun hashCode(): Int = 0x52464653
	override fun toString(): String = "ReaderReleaseFailureFenceSnapshot(<redacted>)"
}

internal enum class ReaderResourceRetirementFenceCapacityStatus { Available, Full }

internal data class ReaderResourceRetirementFenceSanitizedProjection(
	val hasContiguousReleasedPrefix: Boolean,
	val outOfOrderReleasedCount: Int,
	val capacityStatus: ReaderResourceRetirementFenceCapacityStatus
)

internal class ReaderResourceRetirementFenceSnapshot internal constructor(
	private val readerSessionGeneration: Long,
	private val coordinatorEpoch: Long,
	private val contiguousReleasedThrough: Long,
	private val outOfOrderReleasedSequences: Set<Long>
) {
	init {
		require(readerSessionGeneration > 0L)
		require(coordinatorEpoch > 0L)
		require(contiguousReleasedThrough >= 0L)
		require(outOfOrderReleasedSequences.size <= 32)
		require(outOfOrderReleasedSequences.all { it > contiguousReleasedThrough })
	}

	internal fun confirms(order: ReaderResourceRetirementOrder): Boolean =
		order.readerSessionGeneration == readerSessionGeneration &&
			order.coordinatorEpoch == coordinatorEpoch &&
			(order.sequence <= contiguousReleasedThrough || order.sequence in outOfOrderReleasedSequences)

	internal fun sanitizedProjection() = ReaderResourceRetirementFenceSanitizedProjection(
		hasContiguousReleasedPrefix = contiguousReleasedThrough > 0L,
		outOfOrderReleasedCount = outOfOrderReleasedSequences.size,
		capacityStatus = if (outOfOrderReleasedSequences.size == 32) {
			ReaderResourceRetirementFenceCapacityStatus.Full
		} else ReaderResourceRetirementFenceCapacityStatus.Available
	)

	override fun equals(other: Any?): Boolean =
		other is ReaderResourceRetirementFenceSnapshot &&
			readerSessionGeneration == other.readerSessionGeneration &&
			coordinatorEpoch == other.coordinatorEpoch &&
			contiguousReleasedThrough == other.contiguousReleasedThrough &&
			outOfOrderReleasedSequences == other.outOfOrderReleasedSequences
	override fun hashCode(): Int = 0x52524653
	override fun toString(): String = "ReaderResourceRetirementFenceSnapshot(<redacted>)"
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
	private data class AttemptRow(
		val identity: ReaderReleaseCommandIdentity,
		val commandCleanupKey: ReaderReleaseOnlyCleanupKey?,
		var cleanupKey: ReaderReleaseOnlyCleanupKey?,
		var state: ReaderTransitionResourceState
	)

	private data class UnresolvedAdmissionRow(
		val key: ReaderTransitionResourceKey,
		var cleanupKey: ReaderReleaseOnlyCleanupKey?,
		var state: ReaderTransitionResourceState = ReaderTransitionResourceState.Owned
	)

	private data class UnresolvedAttemptAdmissionRow(
		val registration: ReaderTransitionResourceRegistration,
		var cleanupKey: ReaderReleaseOnlyCleanupKey?,
		var state: ReaderTransitionResourceState = ReaderTransitionResourceState.Owned
	)

	private data class UnresolvedAdmissionOverflowRow(
		var cleanupKey: ReaderReleaseOnlyCleanupKey?,
		var state: ReaderTransitionResourceState = ReaderTransitionResourceState.Owned
	)

	private val registrations = linkedMapOf<ReaderTransitionResourceKey, ReaderTransitionResourceRegistration>()
	private val imports = linkedMapOf<ReaderLegacyPhysicalIdentity, ReaderImportedLegacyResourceRegistration>()
	private val physicalByRegistration = linkedMapOf<ReaderTransitionResourceRegistration, ReaderLegacyPhysicalIdentity>()
	private val attempts = linkedMapOf<ReaderTransitionResourceRegistration, AttemptRow>()
	private val unresolvedAdmissions = linkedMapOf<ReaderTransitionResourceKey, UnresolvedAdmissionRow>()
	private val unresolvedAttemptAdmissions = linkedMapOf<
		ReaderTransitionResourceRegistration,
		UnresolvedAttemptAdmissionRow
	>()
	private var unresolvedAdmissionOverflow: UnresolvedAdmissionOverflowRow? = null
	private val transitionTombstones = ReaderTransitionTerminalTombstones()
	private val adoptedTerminalRegistrations = linkedSetOf<ReaderTransitionResourceRegistration>()
	private val failureTombstones = ArrayDeque<ReaderReleaseFailureTombstone>()
	private var lifecycle: Pair<Long, Long>? = null
	private var nextRetirement = 1L
	private var nextImportedKey = 1L
	private var nextAttempt = 1L
	private var contiguousReleased = 0L
	private val outOfOrderReleased = sortedSetOf<Long>()

	fun register(
		key: ReaderTransitionResourceKey,
		session: Long,
		epoch: Long
	): ReaderTransitionResourceRegistration? {
		if (key.ownerId !is ReaderTransitionResourceOwnerId.TransitionOwned) return null
		return registerAuthoritativeKey(key, session, epoch)
	}

	private fun registerAuthoritativeKey(
		key: ReaderTransitionResourceKey,
		session: Long,
		epoch: Long
	): ReaderTransitionResourceRegistration? {
		key.owningTransitionIdOrNull?.let {
			if (it.readerSessionGeneration != session || it.coordinatorEpoch != epoch) return null
		}
		registrations[key]?.let { return it }
		if (
			key in unresolvedAdmissions ||
			!canAdmitLifecycle(session to epoch) ||
			registrations.size >= MaxActive ||
			adoptedTerminalRegistrations.any { it.key == key } ||
			(key.owningTransitionIdOrNull != null && transitionTombstones.rejectsRegistration(key))
		) return null
		return ReaderTransitionResourceRegistration(key, allocateOrder(session, epoch)).also { registrations[key] = it }
	}

	fun register(key: ReaderTransitionResourceKey): Boolean {
		val id = key.owningTransitionIdOrNull ?: return false
		if (key in registrations || adoptedTerminalRegistrations.any { it.key == key }) return false
		return register(key, id.readerSessionGeneration, id.coordinatorEpoch) != null
	}

	fun retainUnresolvedAdmission(
		key: ReaderTransitionResourceKey,
		cleanupKey: ReaderReleaseOnlyCleanupKey?
	): Boolean {
		unresolvedAdmissions[key]?.let { existing ->
			if (existing.cleanupKey == null) existing.cleanupKey = cleanupKey
			return true
		}
		if (
			key in registrations ||
			adoptedTerminalRegistrations.any { it.key == key }
		) return false
		if (unresolvedAdmissions.size >= MaxUnresolvedAdmissions) {
			val overflow = unresolvedAdmissionOverflow
			if (overflow == null) {
				unresolvedAdmissionOverflow = UnresolvedAdmissionOverflowRow(cleanupKey)
			} else if (overflow.cleanupKey == null) {
				overflow.cleanupKey = cleanupKey
			}
			return true
		}
		unresolvedAdmissions[key] = UnresolvedAdmissionRow(key, cleanupKey)
		return true
	}

	fun register(registration: ReaderTransitionResourceRegistration): Boolean {
		if (registration.key.ownerId !is ReaderTransitionResourceOwnerId.TransitionOwned) {
			return false
		}
		if (!canAdmitOrder(registration.retirementOrder)) return false
		if (
			registration.key in registrations ||
			registration.key in unresolvedAdmissions ||
			adoptedTerminalRegistrations.any { it.key == registration.key } ||
			registrations.values.any { it.retirementOrder == registration.retirementOrder } ||
			registrations.size >= MaxActive ||
			(
				registration.key.owningTransitionIdOrNull != null &&
					transitionTombstones.rejectsRegistration(registration.key)
			)
		) return false
		if (!admitOrder(registration.retirementOrder)) return false
		registrations[registration.key] = registration
		return true
	}

	fun importLegacy(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		ownerId: ReaderTransitionResourceOwnerId.AdoptedPredecessor,
		kind: ReaderTransitionResourceKind,
		coordinatorEpoch: Long
	): ReaderImportedLegacyResourceRegistration? {
		imports[physicalIdentity]?.let { existing ->
			return existing.takeIf {
				it.registration.key.ownerId == ownerId &&
					it.registration.key.kind == kind &&
					it.registration.retirementOrder.coordinatorEpoch == coordinatorEpoch
			}
		}
		if (imports.size >= MaxActive || nextImportedKey == Long.MAX_VALUE) return null
		val registration = registerAuthoritativeKey(
			ReaderTransitionResourceKey(ownerId, kind, nextImportedKey++),
			physicalIdentity.domain.readerSessionGeneration,
			coordinatorEpoch
		) ?: return null
		return ReaderImportedLegacyResourceRegistration(physicalIdentity, registration).also {
			imports[physicalIdentity] = it
			physicalByRegistration[registration] = physicalIdentity
		}
	}

	fun rollbackUnpublishedImport(
		imported: ReaderImportedLegacyResourceRegistration
	): Boolean {
		val registration = imported.registration
		val order = registration.retirementOrder
		val key = registration.key
		if (
			imports[imported.physicalIdentity] != imported ||
			physicalByRegistration[registration] != imported.physicalIdentity ||
			registrations[key] != registration ||
			registration in attempts ||
			registration in unresolvedAttemptAdmissions ||
			registration in adoptedTerminalRegistrations ||
			lifecycle != (order.readerSessionGeneration to order.coordinatorEpoch) ||
			nextRetirement != order.sequence + 1L ||
			nextImportedKey != key.opaqueId + 1L ||
			order.sequence <= contiguousReleased ||
			order.sequence in outOfOrderReleased
		) return false
		imports.remove(imported.physicalIdentity)
		physicalByRegistration.remove(registration)
		registrations.remove(key)
		nextRetirement = order.sequence
		nextImportedKey = key.opaqueId
		return true
	}

	fun requestRelease(
		issuer: paige.navic.reader.ReaderResourceReleaseIssuer,
		registration: ReaderTransitionResourceRegistration,
		cleanupKey: ReaderReleaseOnlyCleanupKey? = null
	): ReaderTransitionCommand.ReleaseResource? {
		if (!issuerMatchesRegistration(issuer, registration)) return null
		if (
			registrations[registration.key] != registration ||
			registration in attempts
		) return null
		val unresolved = unresolvedAttemptAdmissions[registration]
		if (unresolved != null && unresolved.state != ReaderTransitionResourceState.Owned) {
			return null
		}
		if (
			unresolved?.cleanupKey != null && cleanupKey != null &&
			unresolved.cleanupKey != cleanupKey
		) return null
		val authoritativeCleanupKey = unresolved?.cleanupKey ?: cleanupKey
		if (
			attempts.size >= MaxActive ||
			failureTombstones.size >= MaxTerminal ||
			wouldExceedAdoptedTerminalCapacity(registration) ||
			wouldExceedRetirementFenceCapacity(registration.retirementOrder) ||
			nextAttempt == Long.MAX_VALUE
		) {
			if (unresolved == null) {
				check(unresolvedAttemptAdmissions.size < MaxActive) {
					"Unresolved release-attempt admission capacity exhausted"
				}
				unresolvedAttemptAdmissions[registration] = UnresolvedAttemptAdmissionRow(
					registration,
					authoritativeCleanupKey
				)
			} else if (unresolved.cleanupKey == null) {
				unresolved.cleanupKey = authoritativeCleanupKey
			}
			return null
		}
		unresolvedAttemptAdmissions.remove(registration)
		val identity = ReaderReleaseCommandIdentity(
			paige.navic.reader.ReaderPhysicalReleaseAttemptId.fromLedger(nextAttempt++), registration
		)
		attempts[registration] = AttemptRow(
			identity = identity,
			commandCleanupKey = authoritativeCleanupKey,
			cleanupKey = authoritativeCleanupKey,
			state = ReaderTransitionResourceState.ReleaseCommandIssued
		)
		return ReaderTransitionCommand.ReleaseResource(issuer, identity, authoritativeCleanupKey)
	}

	private fun wouldExceedAdoptedTerminalCapacity(
		registration: ReaderTransitionResourceRegistration
	): Boolean {
		if (registration.key.ownerId !is ReaderTransitionResourceOwnerId.AdoptedPredecessor) {
			return false
		}
		val reserved = adoptedTerminalRegistrations.size + attempts.keys.count {
			it.key.ownerId is ReaderTransitionResourceOwnerId.AdoptedPredecessor
		}
		return reserved >= MaxTerminal
	}

	private fun wouldExceedRetirementFenceCapacity(
		order: ReaderResourceRetirementOrder
	): Boolean {
		val domain = lifecycle ?: return false
		if (
			order.readerSessionGeneration != domain.first ||
			order.coordinatorEpoch != domain.second ||
			order.sequence <= contiguousReleased + 1L
		) return false
		val reservedGapCount = outOfOrderReleased.size + attempts.values.count { row ->
			val candidate = row.identity.registration.retirementOrder
			candidate.readerSessionGeneration == domain.first &&
				candidate.coordinatorEpoch == domain.second &&
				candidate.sequence > contiguousReleased + 1L
		}
		return reservedGapCount >= MaxTerminal
	}

	private fun issuerMatchesRegistration(
		issuer: ReaderResourceReleaseIssuer,
		registration: ReaderTransitionResourceRegistration
	): Boolean = when (issuer) {
		is ReaderResourceReleaseIssuer.Transition ->
			registration.key.ownerId == ReaderTransitionResourceOwnerId.TransitionOwned(
				issuer.transitionId
			)
		is ReaderResourceReleaseIssuer.Session ->
			registration.key.ownerId is ReaderTransitionResourceOwnerId.AdoptedPredecessor &&
				registration.retirementOrder.readerSessionGeneration ==
					issuer.readerSessionGeneration &&
				registration.retirementOrder.coordinatorEpoch == issuer.coordinatorEpoch
	}

	fun requestRelease(
		key: ReaderTransitionResourceKey,
		cleanupKey: ReaderReleaseOnlyCleanupKey? = null
	): ReaderTransitionCommand.ReleaseResource? {
		val registration = registrations[key] ?: return null
		val issuer = when (val owner = key.ownerId) {
			is ReaderTransitionResourceOwnerId.TransitionOwned -> paige.navic.reader.ReaderResourceReleaseIssuer.Transition(owner.transitionId)
			is ReaderTransitionResourceOwnerId.AdoptedPredecessor -> paige.navic.reader.ReaderResourceReleaseIssuer.Session(
				registration.retirementOrder.readerSessionGeneration, registration.retirementOrder.coordinatorEpoch
			)
		}
		return requestRelease(issuer, registration, cleanupKey)
	}

	fun owns(registration: ReaderTransitionResourceRegistration): Boolean =
		registrations[registration.key] == registration

	fun importedFor(registration: ReaderTransitionResourceRegistration): ReaderImportedLegacyResourceRegistration? =
		physicalByRegistration[registration]?.let(imports::get)

	fun transferOutstandingAttemptsToCleanup(key: ReaderReleaseOnlyCleanupKey): Int {
		var count = 0
		attempts.values.forEach { if (it.cleanupKey == null) { it.cleanupKey = key; count += 1 } }
		unresolvedAdmissions.values.forEach {
			if (it.cleanupKey == null) {
				it.cleanupKey = key
				count += 1
			}
		}
		unresolvedAdmissionOverflow?.let {
			if (it.cleanupKey == null) {
				it.cleanupKey = key
				count += 1
			}
		}
		unresolvedAttemptAdmissions.values.forEach {
			if (it.cleanupKey == null) {
				it.cleanupKey = key
				count += 1
			}
		}
		return count
	}

	fun recordRejectedNoEffect(fact: ReaderTransitionFact.ReleaseCommandRejected): Boolean =
		setFailure(fact.identity, fact.cleanupKey, ReaderTransitionResourceState.RejectedNoEffect)

	fun recordAmbiguousFailure(fact: ReaderTransitionFact.ReleaseCommandThrew): Boolean =
		setFailure(fact.identity, fact.cleanupKey, ReaderTransitionResourceState.AmbiguousFailure)

	fun recordPortContractViolation(fact: ReaderTransitionFact.ReleasePortContractViolated): Boolean =
		matchingOutcome(fact.identity, fact.cleanupKey) != null

	private fun setFailure(identity: ReaderReleaseCommandIdentity, key: ReaderReleaseOnlyCleanupKey?, state: ReaderTransitionResourceState): Boolean {
		val row = matchingOutcome(identity, key) ?: return false
		if (row.state != ReaderTransitionResourceState.ReleaseCommandIssued) return false
		row.state = state
		return true
	}

	private fun matchingOutcome(
		identity: ReaderReleaseCommandIdentity,
		key: ReaderReleaseOnlyCleanupKey?
	): AttemptRow? {
		val row = attempts[identity.registration] ?: return null
		return row.takeIf { it.identity == identity && it.commandCleanupKey == key }
	}

	fun confirmReleased(fact: ReaderTransitionFact.ResourceReleased): Boolean = confirm(null, fact)
	fun confirmLegacyReleased(identity: ReaderLegacyPhysicalIdentity, fact: ReaderTransitionFact.ResourceReleased): Boolean = confirm(identity, fact)

	private fun confirm(physical: ReaderLegacyPhysicalIdentity?, fact: ReaderTransitionFact.ResourceReleased): Boolean {
		val registration = fact.identity.registration
		if (attempts[registration]?.identity != fact.identity) return false
		if (physicalByRegistration[registration] != physical) return false
		attempts.remove(registration)
		registrations.remove(registration.key)
		physicalByRegistration.remove(registration)
		when (registration.key.ownerId) {
			is ReaderTransitionResourceOwnerId.TransitionOwned ->
				check(transitionTombstones.record(registration.key, fromActiveRegistration = true))
			is ReaderTransitionResourceOwnerId.AdoptedPredecessor -> {
				check(adoptedTerminalRegistrations.size < MaxTerminal) {
					"Adopted terminal capacity must be reserved before release dispatch"
				}
				check(adoptedTerminalRegistrations.add(registration))
			}
		}
		advanceFence(registration.retirementOrder)
		return true
	}

	fun markCleanupDeadlineElapsed(key: ReaderReleaseOnlyCleanupKey): Int = terminalize(key, false)
	fun markProcessClosed(key: ReaderReleaseOnlyCleanupKey): Int = terminalize(key, true)

	private fun terminalize(key: ReaderReleaseOnlyCleanupKey, processClose: Boolean): Int {
		var count = 0
		attempts.values.forEach { row ->
			if (row.cleanupKey != key || row.state == ReaderTransitionResourceState.TimedOutUnreleased || row.state == ReaderTransitionResourceState.ProcessClosedUnreleased) return@forEach
			val prior = row.state
			row.state = if (processClose) ReaderTransitionResourceState.ProcessClosedUnreleased else ReaderTransitionResourceState.TimedOutUnreleased
			if (
				(prior == ReaderTransitionResourceState.RejectedNoEffect ||
					prior == ReaderTransitionResourceState.AmbiguousFailure) &&
				failureTombstones.size < MaxTerminal
			) {
				val reason = when (prior to processClose) {
					ReaderTransitionResourceState.RejectedNoEffect to false -> ReaderReleaseFailureTombstoneReason.RejectedNoEffectTimedOut
					ReaderTransitionResourceState.RejectedNoEffect to true -> ReaderReleaseFailureTombstoneReason.RejectedNoEffectProcessClosed
					ReaderTransitionResourceState.AmbiguousFailure to false -> ReaderReleaseFailureTombstoneReason.AmbiguousFailureTimedOut
					else -> ReaderReleaseFailureTombstoneReason.AmbiguousFailureProcessClosed
				}
				failureTombstones += ReaderReleaseFailureTombstone(key, row.identity, reason)
			}
			count += 1
		}
		unresolvedAdmissions.values.forEach { row ->
			if (
				row.cleanupKey != key ||
				row.state == ReaderTransitionResourceState.TimedOutUnreleased ||
				row.state == ReaderTransitionResourceState.ProcessClosedUnreleased
			) return@forEach
			row.state = if (processClose) {
				ReaderTransitionResourceState.ProcessClosedUnreleased
			} else {
				ReaderTransitionResourceState.TimedOutUnreleased
			}
			count += 1
		}
		unresolvedAdmissionOverflow?.let { row ->
			if (
				row.cleanupKey == key &&
				row.state != ReaderTransitionResourceState.TimedOutUnreleased &&
				row.state != ReaderTransitionResourceState.ProcessClosedUnreleased
			) {
				row.state = if (processClose) {
					ReaderTransitionResourceState.ProcessClosedUnreleased
				} else {
					ReaderTransitionResourceState.TimedOutUnreleased
				}
				count += 1
			}
		}
		unresolvedAttemptAdmissions.values.forEach { row ->
			if (
				row.cleanupKey != key ||
				row.state == ReaderTransitionResourceState.TimedOutUnreleased ||
				row.state == ReaderTransitionResourceState.ProcessClosedUnreleased
			) return@forEach
			row.state = if (processClose) {
				ReaderTransitionResourceState.ProcessClosedUnreleased
			} else {
				ReaderTransitionResourceState.TimedOutUnreleased
			}
			count += 1
		}
		return count
	}

	fun cleanupStatus(key: ReaderReleaseOnlyCleanupKey): ReaderReleaseLedgerCleanupStatus {
		val attemptRows = attempts.values.filter { it.cleanupKey == key }
		val unresolvedRows = unresolvedAdmissions.values.filter { it.cleanupKey == key }
		val overflowRow = unresolvedAdmissionOverflow?.takeIf { it.cleanupKey == key }
		val unresolvedAttemptRows = unresolvedAttemptAdmissions.values.filter { it.cleanupKey == key }
		return when {
			attemptRows.isEmpty() && unresolvedRows.isEmpty() && overflowRow == null &&
				unresolvedAttemptRows.isEmpty() -> ReaderReleaseLedgerCleanupStatus.EmptyReleased
			attemptRows.any {
				it.state != ReaderTransitionResourceState.TimedOutUnreleased &&
					it.state != ReaderTransitionResourceState.ProcessClosedUnreleased
			} || unresolvedRows.any {
				it.state != ReaderTransitionResourceState.TimedOutUnreleased &&
					it.state != ReaderTransitionResourceState.ProcessClosedUnreleased
			} || overflowRow?.let {
				it.state != ReaderTransitionResourceState.TimedOutUnreleased &&
					it.state != ReaderTransitionResourceState.ProcessClosedUnreleased
			} == true || unresolvedAttemptRows.any {
				it.state != ReaderTransitionResourceState.TimedOutUnreleased &&
					it.state != ReaderTransitionResourceState.ProcessClosedUnreleased
			} -> ReaderReleaseLedgerCleanupStatus.Open
			else -> ReaderReleaseLedgerCleanupStatus.TerminalFailure
		}
	}

	fun stateOf(key: ReaderTransitionResourceKey): ReaderTransitionResourceState? {
		unresolvedAdmissions[key]?.let { return it.state }
		val registration = registrations[key]
		if (registration != null) {
			return attempts[registration]?.state ?: unresolvedAttemptAdmissions[registration]?.state
				?: ReaderTransitionResourceState.Owned
		}
		val released = when (key.ownerId) {
			is ReaderTransitionResourceOwnerId.TransitionOwned ->
				transitionTombstones.containsOrFenced(key)
			is ReaderTransitionResourceOwnerId.AdoptedPredecessor ->
				adoptedTerminalRegistrations.any { it.key == key }
		}
		return ReaderTransitionResourceState.Released.takeIf { released }
	}

	fun retirementFence(): ReaderResourceRetirementFenceSnapshot {
		val domain = lifecycle ?: (1L to 1L)
		return ReaderResourceRetirementFenceSnapshot(domain.first, domain.second, contiguousReleased, outOfOrderReleased.toSet())
	}
	fun failureFence() = ReaderReleaseFailureFenceSnapshot(failureTombstones.toSet())
	fun snapshot() = ReaderTransitionReleaseLedgerSnapshot(
		registrations.values.count { registration ->
			registration !in attempts &&
				(unresolvedAttemptAdmissions[registration]?.state
					?: ReaderTransitionResourceState.Owned) == ReaderTransitionResourceState.Owned
		} + unresolvedAdmissions.values.count {
			it.state == ReaderTransitionResourceState.Owned
		} + if (unresolvedAdmissionOverflow?.state == ReaderTransitionResourceState.Owned) 1 else 0,
		attempts.size,
		transitionTombstones.terminalSnapshot().terminalTombstoneCount +
			adoptedTerminalRegistrations.size
	)
	fun retentionSnapshot() = ReaderTransitionReleaseLedgerRetentionSnapshot(
		registrations.size + unresolvedAdmissions.size +
			if (unresolvedAdmissionOverflow == null) 0 else 1,
		0,
		transitionTombstones.terminalSnapshot().terminalTombstoneCount,
		MaxTerminal,
		if (lifecycle == null) {
			ReaderTransitionRetirementFenceState.Inactive
		} else {
			ReaderTransitionRetirementFenceState.Active
		}
	)

	private fun canAdmitLifecycle(domain: Pair<Long, Long>): Boolean {
		val current = lifecycle ?: return true
		return compareValuesBy(
			domain,
			current,
			Pair<Long, Long>::first,
			Pair<Long, Long>::second
		) >= 0
	}

	private fun allocateOrder(session: Long, epoch: Long): ReaderResourceRetirementOrder {
		val domain = session to epoch
		if (lifecycle != domain) {
			check(canAdmitLifecycle(domain)) { "Cannot restore a retired retirement domain" }
			lifecycle = domain
			nextRetirement = 1L
			contiguousReleased = 0L
			outOfOrderReleased.clear()
		}
		check(nextRetirement < Long.MAX_VALUE)
		return ReaderResourceRetirementOrder(session, epoch, nextRetirement++)
	}

	private fun canAdmitOrder(order: ReaderResourceRetirementOrder): Boolean {
		if (order.sequence == Long.MAX_VALUE) return false
		val domain = order.readerSessionGeneration to order.coordinatorEpoch
		val current = lifecycle
		if (current == null || domain != current) return canAdmitLifecycle(domain)
		return order.sequence > contiguousReleased && order.sequence !in outOfOrderReleased
	}

	private fun admitOrder(order: ReaderResourceRetirementOrder): Boolean {
		val domain = order.readerSessionGeneration to order.coordinatorEpoch
		val current = lifecycle
		if (current == null || domain != current) {
			if (!canAdmitLifecycle(domain) || order.sequence == Long.MAX_VALUE) return false
			lifecycle = domain
			nextRetirement = order.sequence + 1L
			contiguousReleased = 0L
			outOfOrderReleased.clear()
			return true
		}
		if (
			order.sequence <= contiguousReleased ||
			order.sequence in outOfOrderReleased ||
			order.sequence == Long.MAX_VALUE
		) return false
		nextRetirement = maxOf(nextRetirement, order.sequence + 1L)
		return true
	}

	private fun advanceFence(order: ReaderResourceRetirementOrder) {
		val domain = lifecycle ?: return
		if (
			order.readerSessionGeneration != domain.first ||
			order.coordinatorEpoch != domain.second
		) return
		val sequence = order.sequence
		if (sequence == contiguousReleased + 1L) {
			contiguousReleased = sequence
			while (outOfOrderReleased.remove(contiguousReleased + 1L)) contiguousReleased += 1L
		} else if (sequence > contiguousReleased + 1L) {
			check(outOfOrderReleased.size < MaxTerminal) {
				"Retirement fence capacity must be reserved before release dispatch"
			}
			outOfOrderReleased += sequence
		}
	}
	private companion object {
		const val MaxActive = 64
		const val MaxTerminal = 32
		const val MaxUnresolvedAdmissions = 32
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
			val trustedSemanticViolation: Boolean = false,
			val importedLegacyIdentity: ReaderLegacyPhysicalIdentity? = null
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
		data class ReleaseCleanupDeadlineElapsed(
			val key: ReaderReleaseOnlyCleanupKey,
			val token: Long
		) : MailboxEntry
		data class ActivatedReleaseCleanupDeadlineElapsed(
			val key: ReaderReleaseOnlyCleanupKey,
			val token: Long
		) : MailboxEntry
		data class ReleaseCleanupProcessClosed(
			val key: ReaderReleaseOnlyCleanupKey
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
	private val retiredTask6TimerCallbacks = linkedSetOf<ReaderTask6FactOnlyTimerRegistration>()
	private val releaseAdmissionFailures = ArrayDeque<
		ReaderTransitionFact.ReleaseLedgerAdmissionRejected
	>()
	private var nextDeadlineSlotToken = 1L
	private var releaseCleanupDeadlineSlot: ReleaseCleanupDeadlineSlot? = null
	private var activatedReleaseCleanupDeadlineSlot: ActivatedReleaseCleanupDeadlineSlot? = null
	private var nextReleaseCleanupDeadlineToken = 1L
	private var processClosedCleanupKey: ReaderReleaseOnlyCleanupKey? = null
	private var releaseOnlySink = false

	init {
		journal.resourceRegistrations().forEach { registration ->
			check(
				releaseLedger.owns(registration) || releaseLedger.register(registration)
			) { "Reader transition journal requires its exact release ledger" }
			if (registration.key.ownerId is ReaderTransitionResourceOwnerId.AdoptedPredecessor) {
				check(releaseLedger.importedFor(registration) != null) {
					"Adopted journal authority requires an exact imported release registration"
				}
			}
		}
		journal.resourceKeys().forEach { key ->
			check(
				releaseLedger.stateOf(key) != null || releaseLedger.register(key)
			) { "Reader transition journal resource cannot be admitted to its release ledger" }
		}
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

	private fun enqueuePhysicalReleaseFact(
		fact: ReaderTransitionFact,
		importedLegacyIdentity: ReaderLegacyPhysicalIdentity? = null
	) {
		if (!ports.acceptsFact(fact)) return
		appendMailboxEntry(
			MailboxEntry.Fact(
				fact = fact,
				importedLegacyIdentity = importedLegacyIdentity.takeIf {
					fact is ReaderTransitionFact.ResourceReleased
				}
			)
		)
	}

	fun enqueueProcessClosed(cleanupKey: ReaderReleaseOnlyCleanupKey) {
		appendMailboxEntry(MailboxEntry.ReleaseCleanupProcessClosed(cleanupKey))
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
			scheduledCallbackCount = (if (deadlineSlot == null) 0 else 1) +
				task6TimerOwnership.count +
				(if (releaseCleanupDeadlineSlot == null) 0 else 1) +
				(if (activatedReleaseCleanupDeadlineSlot == null) 0 else 1),
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
				if (entry is MailboxEntry.ReleaseCleanupDeadlineElapsed) {
					processReleaseCleanupDeadlineElapsed(entry)
					continue
				}
				if (entry is MailboxEntry.ActivatedReleaseCleanupDeadlineElapsed) {
					processActivatedReleaseCleanupDeadlineElapsed(entry)
					continue
				}
				if (entry is MailboxEntry.ReleaseCleanupProcessClosed) {
					processReleaseCleanupProcessClosed(entry)
					continue
				}
				val factEntry = entry as MailboxEntry.Fact
				val fact = factEntry.fact
				val authoritativeTask6Expiry = accountAuthoritativeTask6Expiry(fact)
				val before = journal
				val nowMillis = ports.clock.nowMillis()
				val registeredKey = fact.registeredResourceKeyOrNull()
				val registrationAdmissionRejected = registeredKey != null &&
					releaseLedger.stateOf(registeredKey) == null &&
					!releaseLedger.register(registeredKey)
				if (registrationAdmissionRejected) {
					enqueueReleaseLedgerAdmissionRejected(
						requireNotNull(registeredKey),
						journal.releaseOnlyCleanup?.key,
						ReaderReleaseLedgerAdmissionRejectionReason.RegistrationRejected
					)
				}
				val exactReleaseConfirmation = when (fact) {
					is ReaderTransitionFact.ResourceReleased -> {
						val imported = factEntry.importedLegacyIdentity
						if (imported == null) {
							releaseLedger.confirmReleased(fact)
						} else {
							releaseLedger.confirmLegacyReleased(imported, fact)
						}
					}
					else -> null
				}
				when (fact) {
					is ReaderTransitionFact.ReleaseCommandRejected -> releaseLedger.recordRejectedNoEffect(fact)
					is ReaderTransitionFact.ReleaseCommandThrew -> releaseLedger.recordAmbiguousFailure(fact)
					is ReaderTransitionFact.ReleasePortContractViolated -> releaseLedger.recordPortContractViolation(fact)
					else -> Unit
				}
				val classification = if (exactReleaseConfirmation == false) {
					ReaderTransitionFactClassification.StaleTransition
				} else {
					classify(fact, before)
				}
				classificationCounts[classification] =
					classificationCounts.getOrElse(classification) { 0 } + 1
				val reduction = when {
					registrationAdmissionRejected ->
						paige.navic.reader.ReaderTransitionReduction(before, emptyList())
					exactReleaseConfirmation == false ->
						paige.navic.reader.ReaderTransitionReduction(before, emptyList())
					authoritativeTask6Expiry && before.releaseOnlyCleanup != null ->
						paige.navic.reader.ReaderTransitionReduction(
							before.markReleaseOnlyCleanupDeadlineElapsed(
								requireNotNull(before.releaseOnlyCleanup).key
							),
							emptyList()
						)
					factEntry.trustedSemanticViolation -> journal.reduceTrustedSemanticPortContractViolation(
						fact as ReaderTransitionFact.SemanticPortContractViolated,
						factEntry.trustedSemanticAuthority
					)
					releaseOnlySink &&
						fact !is ReaderTransitionFact.OwnedWorkCancellationCompleted &&
						fact !is ReaderTransitionFact.ReleaseLedgerAdmissionRejected ->
						paige.navic.reader.ReaderTransitionReduction(before, emptyList())
					else -> journal.reduce(fact, nowMillis)
				}

				// These assignments are the coordinator's publication barrier: callbacks may run
				// synchronously from either deadline registration or command issuance below.
				journal = reduction.state
				val cleanup = journal.releaseOnlyCleanup
				val cleanupKey = cleanup?.key
				if (cleanupKey != null && before.releaseOnlyCleanup?.key != cleanupKey) {
					releaseLedger.transferOutstandingAttemptsToCleanup(cleanupKey)
				}
				if (cleanup != null) {
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
						if (mode == ReaderTransitionMode.Active) {
							requestReleaseOrReject(registeredKey, cleanupKey)?.let(::add)
						} else {
							val owner = requireNotNull(registeredKey.owningTransitionIdOrNull)
							add(
								ReaderTransitionCommand.RequestResourceRelease(
									ReaderResourceReleaseIssuer.Transition(owner),
									registeredKey,
									cleanupKey = cleanupKey
								)
							)
						}
					}
				}
				recordShadowPrediction(fact, classification, before, predictedCommands)

				if (mode == ReaderTransitionMode.Active) {
					var commandsToIssue = predictedCommands.mapNotNull { predicted ->
						if (commandDispatchAllowed || predicted.pendingStageOrNull() == null) {
							accountCommand(predicted)
						} else {
							null
						}
					}
					if (cleanupKey != null && processClosedCleanupKey == cleanupKey) {
						releaseLedger.markProcessClosed(cleanupKey)
						journal = journal.markReleaseOnlyCleanupProcessClosed(cleanupKey)
						commandsToIssue = commandsToIssue.filterNot {
							it is ReaderTransitionCommand.ReleaseResource
						}
					}
					reconcileReleaseCleanupDeadline(nowMillis, cleanupKey)
					val cleanupDeadlineStatus = journal.releaseOnlyCleanup?.deadlineStatus
					if (
						cleanupDeadlineStatus != ReaderReleaseOnlyCleanupDeadlineStatus.BindingRejected &&
						cleanupDeadlineStatus != ReaderReleaseOnlyCleanupDeadlineStatus.BindingThrew
					) {
						commandsToIssue.forEach(::issueCommandWithPublicationAcknowledgement)
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
		if (command is ReaderTransitionCommand.ReleaseResource) {
			val imported = releaseLedger.importedFor(command.registration)
			if (imported != null) {
				ports.issueImportedLegacyRelease(
					ReaderImportedLegacyReleaseDispatch(command, imported)
				) { fact ->
					enqueuePhysicalReleaseFact(fact, imported.physicalIdentity)
				}
				return
			}
		}
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
				if (command is ReaderTransitionCommand.ReleaseResource) {
					ports.issue(command) { fact -> enqueuePhysicalReleaseFact(fact) }
				} else {
					ports.issue(command, ::enqueue)
				}
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
		when (command) {
			is ReaderTransitionCommand.RequestResourceRelease -> {
				val registration = command.registration?.let { supplied ->
					when {
						releaseLedger.owns(supplied) -> supplied
						releaseLedger.register(supplied) -> supplied
						else -> null
					}
				} ?: if (command.registration == null) {
					val id = requireNotNull(command.key.owningTransitionIdOrNull)
					releaseLedger.register(
						command.key,
						id.readerSessionGeneration,
						id.coordinatorEpoch
					)
				} else {
					null
				}
				if (registration == null) {
					val state = releaseLedger.stateOf(command.key)
					if (state == null || state == ReaderTransitionResourceState.Owned) {
						enqueueReleaseLedgerAdmissionRejected(
							command.key,
							command.cleanupKey,
							ReaderReleaseLedgerAdmissionRejectionReason.RegistrationRejected,
							command
						)
					}
					null
				} else {
					val release = releaseLedger.requestRelease(
						command.issuer,
						registration,
						command.cleanupKey
					)
					if (
						release == null &&
						releaseLedger.stateOf(command.key) == ReaderTransitionResourceState.Owned
					) {
						enqueueReleaseLedgerAdmissionRejected(
							command.key,
							command.cleanupKey,
							ReaderReleaseLedgerAdmissionRejectionReason.AttemptRejected,
							command
						)
					}
					release
				}
			}
			else -> command
		}

	private fun requestReleaseOrReject(
		key: ReaderTransitionResourceKey,
		cleanupKey: ReaderReleaseOnlyCleanupKey?
	): ReaderTransitionCommand.ReleaseResource? {
		val release = releaseLedger.requestRelease(key, cleanupKey)
		if (
			release == null &&
			releaseLedger.stateOf(key) == ReaderTransitionResourceState.Owned
		) {
			enqueueReleaseLedgerAdmissionRejected(
				key,
				cleanupKey,
				ReaderReleaseLedgerAdmissionRejectionReason.AttemptRejected
			)
		}
		return release
	}

	private fun enqueueReleaseLedgerAdmissionRejected(
		key: ReaderTransitionResourceKey,
		cleanupKey: ReaderReleaseOnlyCleanupKey?,
		reason: ReaderReleaseLedgerAdmissionRejectionReason,
		rejectedRequest: ReaderTransitionCommand.RequestResourceRelease? = null
	) {
		if (reason == ReaderReleaseLedgerAdmissionRejectionReason.RegistrationRejected) {
			check(releaseLedger.retainUnresolvedAdmission(key, cleanupKey)) {
				"Bounded unresolved release-admission authority exhausted"
			}
		}
		val failure = ReaderTransitionFact.ReleaseLedgerAdmissionRejected(
			key,
			cleanupKey,
			reason,
			rejectedRequest
		)
		if (failure in releaseAdmissionFailures) return
		if (releaseAdmissionFailures.size == MaxReleaseAdmissionFailures) {
			releaseAdmissionFailures.removeFirst()
		}
		releaseAdmissionFailures.addLast(failure)
		appendMailboxEntry(MailboxEntry.Fact(failure))
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
			if (active == null && journal.releaseOnlyCleanup != null) return true
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
					registration != null -> routeTask6TimerExpiry(expired, registration)
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

	private fun retireTask6TimerForReleaseCleanup(
		port: ReaderTask6FactOnlyTimerPort?,
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderReleaseOnlyCleanupDeadlineStatus? {
		check(task6TimerOwnership.contains(registration))
		val priorFailure = task6TimerOwnership.failure(registration)
		val expiryAlreadyObserved = task6AuthoritativeExpiries.removeAll {
			it.registration == registration
		}
		task6TimerOwnership.clear(registration)
		if (expiryAlreadyObserved) return null
		check(
			retiredTask6TimerCallbacks.size < MaxRetiredTask6TimerCallbacks ||
				registration in retiredTask6TimerCallbacks
		) { "Retired Task 6 callback authority is bounded" }
		retiredTask6TimerCallbacks += registration
		if (priorFailure != null) return priorFailure
		val disposition = attemptTask6TimerCancellation(port, registration)
		if (disposition == null) retiredTask6TimerCallbacks.remove(registration)
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

	private fun routeTask6TimerExpiry(
		fact: ReaderTransitionFact.DeadlineExpired,
		registration: ReaderTask6FactOnlyTimerRegistration
	) {
		if (fact.transitionId != registration.transitionId) return
		when {
			task6TimerOwnership.contains(registration) ->
				enqueueAuthoritativeTask6Expiry(fact, registration)
			registration in retiredTask6TimerCallbacks ->
				retiredTask6TimerCallbacks.remove(registration)
		}
	}

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

	private fun accountAuthoritativeTask6Expiry(fact: ReaderTransitionFact): Boolean {
		val expiry = fact as? ReaderTransitionFact.DeadlineExpired ?: return false
		val evidenceIndex = task6AuthoritativeExpiries.indexOfFirst { it.fact === expiry }
		if (evidenceIndex < 0) return false
		val registration = task6AuthoritativeExpiries.removeAt(evidenceIndex).registration
		task6TimerOwnership.clear(registration)
		return true
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

	private fun reconcileReleaseCleanupDeadline(
		nowMillis: Long,
		cleanupKey: ReaderReleaseOnlyCleanupKey?
	) {
		if (activationState() == ReaderSessionActivationState.Activated) {
			cancelReleaseCleanupDeadline()
			reconcileActivatedReleaseCleanup(cleanupKey)
			return
		}
		val cleanup = journal.releaseOnlyCleanup
		if (cleanupKey == null || cleanup?.key != cleanupKey) {
			cancelReleaseCleanupDeadline()
			return
		}
		if (processClosedCleanupKey == cleanupKey) {
			releaseLedger.markProcessClosed(cleanupKey)
			journal = journal.markReleaseOnlyCleanupProcessClosed(cleanupKey)
			cancelReleaseCleanupDeadline()
			return
		}
		when (cleanup.deadlineStatus) {
			ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed -> {
				releaseLedger.markCleanupDeadlineElapsed(cleanupKey)
				cancelReleaseCleanupDeadline()
				return
			}
			ReaderReleaseOnlyCleanupDeadlineStatus.ProcessClosed -> {
				releaseLedger.markProcessClosed(cleanupKey)
				cancelReleaseCleanupDeadline()
				return
			}
			ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting -> {
				if (releaseLedger.cleanupStatus(cleanupKey) == ReaderReleaseLedgerCleanupStatus.Open) {
					releaseLedger.markCleanupDeadlineElapsed(cleanupKey)
					journal = journal.markReleaseOnlyCleanupDeadlineElapsed(cleanupKey)
				}
				cancelReleaseCleanupDeadline()
				return
			}
			ReaderReleaseOnlyCleanupDeadlineStatus.CancellationRejected,
			ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew -> return
			else -> Unit
		}
		val cancellationAccounted = when (cleanup.cancellationStatus) {
			ReaderReleaseOnlyCancellationStatus.NotRequired,
			ReaderReleaseOnlyCancellationStatus.Applied,
			ReaderReleaseOnlyCancellationStatus.Rejected,
			ReaderReleaseOnlyCancellationStatus.Threw -> true
			ReaderReleaseOnlyCancellationStatus.Pending,
			ReaderReleaseOnlyCancellationStatus.ProcessClosedPending -> false
		}
		when (releaseLedger.cleanupStatus(cleanupKey)) {
			ReaderReleaseLedgerCleanupStatus.TerminalFailure -> {
				journal = journal.markReleaseOnlyCleanupDeadlineElapsed(cleanupKey)
				cancelReleaseCleanupDeadline()
				return
			}
			ReaderReleaseLedgerCleanupStatus.EmptyReleased -> if (cancellationAccounted) {
				val cancellationFailure = cancelReleaseCleanupDeadline()
				val currentCleanup = journal.releaseOnlyCleanup?.takeIf { it.key == cleanupKey }
				if (
					cancellationFailure == null &&
					currentCleanup?.deadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Armed
				) {
					journal = journal.copy(
						releaseOnlyCleanup = currentCleanup.copy(
							deadlineStatus = ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting
						)
					)
				}
				return
			}
			ReaderReleaseLedgerCleanupStatus.EmptyReleased,
			ReaderReleaseLedgerCleanupStatus.Open -> Unit
		}
		if (releaseCleanupDeadlineSlot?.key == cleanupKey) return
		cancelReleaseCleanupDeadline()
		val token = nextReleaseCleanupDeadlineToken
		check(token < Long.MAX_VALUE) { "Release cleanup deadline token exhausted" }
		nextReleaseCleanupDeadlineToken = token + 1L
		val atMillis = nowMillis.saturatingAdd(ReleaseCleanupDeadlineMillis)
		val slot = ReleaseCleanupDeadlineSlot(cleanupKey, token, atMillis)
		releaseCleanupDeadlineSlot = slot
		val registration = try {
			ports.clock.schedule(atMillis) {
				slot.expirationObserved = true
				appendMailboxEntry(
					MailboxEntry.ReleaseCleanupDeadlineElapsed(cleanupKey, token)
				)
			}
		} catch (_: Throwable) {
			null
		}
		if (releaseCleanupDeadlineSlot?.token == token) {
			slot.registration = registration
			if (registration == null) {
				slot.expirationObserved = true
				appendMailboxEntry(
					MailboxEntry.ReleaseCleanupDeadlineElapsed(cleanupKey, token)
				)
			}
		} else {
			try {
				registration?.cancel()
			} catch (_: Throwable) {
			}
		}
	}

	private fun reconcileActivatedReleaseCleanup(
		cleanupKey: ReaderReleaseOnlyCleanupKey?
	) {
		val cleanup = journal.releaseOnlyCleanup
		if (cleanupKey == null || cleanup?.key != cleanupKey) return
		if (processClosedCleanupKey == cleanupKey) {
			releaseLedger.markProcessClosed(cleanupKey)
			journal = journal.markReleaseOnlyCleanupProcessClosed(cleanupKey)
			discardTask6CleanupTimers()
			return
		}
		when (cleanup.deadlineStatus) {
			ReaderReleaseOnlyCleanupDeadlineStatus.ProcessClosed -> {
				releaseLedger.markProcessClosed(cleanupKey)
				discardTask6CleanupTimers()
				return
			}
			ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed,
			ReaderReleaseOnlyCleanupDeadlineStatus.BindingRejected,
			ReaderReleaseOnlyCleanupDeadlineStatus.BindingThrew -> {
				releaseLedger.markCleanupDeadlineElapsed(cleanupKey)
				return
			}
			ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting -> {
				if (releaseLedger.cleanupStatus(cleanupKey) == ReaderReleaseLedgerCleanupStatus.Open) {
					releaseLedger.markCleanupDeadlineElapsed(cleanupKey)
					journal = journal.markReleaseOnlyCleanupDeadlineElapsed(cleanupKey)
				}
				return
			}
			ReaderReleaseOnlyCleanupDeadlineStatus.Armed -> Unit
			ReaderReleaseOnlyCleanupDeadlineStatus.CancellationRejected,
			ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew -> return
		}
		val ledgerStatus = releaseLedger.cleanupStatus(cleanupKey)
		if (ledgerStatus == ReaderReleaseLedgerCleanupStatus.TerminalFailure) {
			journal = journal.markReleaseOnlyCleanupDeadlineElapsed(cleanupKey)
			return
		}
		val cancellationAccounted = when (cleanup.cancellationStatus) {
			ReaderReleaseOnlyCancellationStatus.NotRequired,
			ReaderReleaseOnlyCancellationStatus.Applied,
			ReaderReleaseOnlyCancellationStatus.Rejected,
			ReaderReleaseOnlyCancellationStatus.Threw -> true
			ReaderReleaseOnlyCancellationStatus.Pending,
			ReaderReleaseOnlyCancellationStatus.ProcessClosedPending -> false
		}
		if (ledgerStatus == ReaderReleaseLedgerCleanupStatus.EmptyReleased && cancellationAccounted) {
			cancelActivatedReleaseCleanupDeadline(terminalAccounting = true)
			cancelTask6TimersForTerminalState(ports.task6FactOnlyTimer)
			return
		}
		if (activatedReleaseCleanupDeadlineSlot?.key == cleanupKey) return
		if (task6TimerOwnership.count > 0) {
			task6TimerOwnership.registrations.toList().forEach { registration ->
				retireTask6TimerForReleaseCleanup(
					ports.task6FactOnlyTimer,
					registration
				)
			}
		}
		bindActivatedReleaseCleanupDeadline(cleanupKey)
	}

	private fun bindActivatedReleaseCleanupDeadline(cleanupKey: ReaderReleaseOnlyCleanupKey) {
		val token = nextReleaseCleanupDeadlineToken
		check(token < Long.MAX_VALUE) { "Release cleanup deadline token exhausted" }
		nextReleaseCleanupDeadlineToken = token + 1L
		val slot = ActivatedReleaseCleanupDeadlineSlot(cleanupKey, token)
		activatedReleaseCleanupDeadlineSlot = slot
		val port = ports.task6FactOnlyTimer
		val registration = try {
			port?.bindReleaseOnlyCloseBudget(cleanupKey) { expiredKey ->
				if (expiredKey == cleanupKey) {
					appendMailboxEntry(
						MailboxEntry.ActivatedReleaseCleanupDeadlineElapsed(cleanupKey, token)
					)
				}
			}
		} catch (_: Throwable) {
			activatedReleaseCleanupDeadlineSlot = null
			failActivatedReleaseCleanupBinding(
				cleanupKey,
				ReaderReleaseOnlyCleanupDeadlineStatus.BindingThrew
			)
			return
		}
		if (registration == null) {
			activatedReleaseCleanupDeadlineSlot = null
			failActivatedReleaseCleanupBinding(
				cleanupKey,
				ReaderReleaseOnlyCleanupDeadlineStatus.BindingRejected
			)
			return
		}
		if (activatedReleaseCleanupDeadlineSlot?.token == token) {
			slot.registration = registration
		} else {
			try {
				port?.cancelReleaseOnlyCloseBudget(registration)
			} catch (_: Throwable) {
			}
		}
	}

	private fun failActivatedReleaseCleanupBinding(
		cleanupKey: ReaderReleaseOnlyCleanupKey,
		status: ReaderReleaseOnlyCleanupDeadlineStatus
	) {
		require(
			status == ReaderReleaseOnlyCleanupDeadlineStatus.BindingRejected ||
				status == ReaderReleaseOnlyCleanupDeadlineStatus.BindingThrew
		)
		val cleanup = journal.releaseOnlyCleanup?.takeIf { it.key == cleanupKey } ?: return
		releaseLedger.markCleanupDeadlineElapsed(cleanupKey)
		journal = journal.copy(
			releaseOnlyCleanup = cleanup.copy(deadlineStatus = status),
			lastOutcome = ReaderTransitionOutcome.Failed(
				paige.navic.reader.ReaderTransitionFailureReason.CloseDrainTimeout,
				paige.navic.reader.ReaderTransitionRetryability.NonRetryable,
				paige.navic.reader.ReaderPresentationFrameOwner.Neutral
			),
			retryableTransition = null
		)
	}

	private fun cancelActivatedReleaseCleanupDeadline(terminalAccounting: Boolean) {
		val slot = activatedReleaseCleanupDeadlineSlot
		if (slot == null) {
			val cleanup = journal.releaseOnlyCleanup
			if (
				terminalAccounting &&
				cleanup?.deadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Armed
			) {
				journal = journal.copy(
					releaseOnlyCleanup = cleanup.copy(
						deadlineStatus = ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting
					)
				)
			}
			return
		}
		val registration = slot.registration ?: return
		val disposition = try {
			when (ports.task6FactOnlyTimer?.cancelReleaseOnlyCloseBudget(registration)) {
				ReaderPortCommandResult.Accepted -> null
				is ReaderPortCommandResult.Rejected,
				null -> ReaderReleaseOnlyCleanupDeadlineStatus.CancellationRejected
			}
		} catch (_: Throwable) {
			ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew
		}
		val cleanup = journal.releaseOnlyCleanup?.takeIf { it.key == slot.key } ?: return
		if (disposition == null) {
			activatedReleaseCleanupDeadlineSlot = null
			if (terminalAccounting && cleanup.deadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Armed) {
				journal = journal.copy(
					releaseOnlyCleanup = cleanup.copy(
						deadlineStatus = ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting
					)
				)
			}
		} else {
			journal = journal.copy(
				releaseOnlyCleanup = cleanup.copy(deadlineStatus = disposition),
				lastOutcome = ReaderTransitionOutcome.Failed(
					paige.navic.reader.ReaderTransitionFailureReason.CloseDrainTimeout,
					paige.navic.reader.ReaderTransitionRetryability.NonRetryable,
					paige.navic.reader.ReaderPresentationFrameOwner.Neutral
				),
				retryableTransition = null
			)
		}
	}

	private fun discardTask6CleanupTimers() {
		activatedReleaseCleanupDeadlineSlot?.let { slot ->
			try {
				slot.registration?.let { registration ->
					ports.task6FactOnlyTimer?.cancelReleaseOnlyCloseBudget(registration)
				}
			} catch (_: Throwable) {
			}
			activatedReleaseCleanupDeadlineSlot = null
		}
		task6TimerOwnership.registrations.toList().forEach { registration ->
			attemptTask6TimerCancellation(ports.task6FactOnlyTimer, registration)
			task6TimerOwnership.clear(registration)
			task6AuthoritativeExpiries.removeAll { it.registration == registration }
		}
		retiredTask6TimerCallbacks.clear()
	}

	private fun processActivatedReleaseCleanupDeadlineElapsed(
		entry: MailboxEntry.ActivatedReleaseCleanupDeadlineElapsed
	) {
		val slot = activatedReleaseCleanupDeadlineSlot ?: return
		if (slot.key != entry.key || slot.token != entry.token) return
		activatedReleaseCleanupDeadlineSlot = null
		if (journal.releaseOnlyCleanup?.key != entry.key) return
		releaseLedger.markCleanupDeadlineElapsed(entry.key)
		journal = journal.markReleaseOnlyCleanupDeadlineElapsed(entry.key)
	}

	private fun processReleaseCleanupDeadlineElapsed(
		entry: MailboxEntry.ReleaseCleanupDeadlineElapsed
	) {
		val slot = releaseCleanupDeadlineSlot ?: return
		if (slot.key != entry.key || slot.token != entry.token) return
		releaseCleanupDeadlineSlot = null
		try {
			slot.registration?.cancel()
		} catch (_: Throwable) {
		}
		if (journal.releaseOnlyCleanup?.key != entry.key) return
		releaseLedger.markCleanupDeadlineElapsed(entry.key)
		journal = journal.markReleaseOnlyCleanupDeadlineElapsed(entry.key)
	}

	private fun processReleaseCleanupProcessClosed(
		entry: MailboxEntry.ReleaseCleanupProcessClosed
	) {
		if (journal.releaseOnlyCleanup?.key != entry.key) return
		processClosedCleanupKey = entry.key
		cancelReleaseCleanupDeadline()
		releaseLedger.markProcessClosed(entry.key)
		journal = journal.markReleaseOnlyCleanupProcessClosed(entry.key)
		if (activationState() == ReaderSessionActivationState.Activated) {
			discardTask6CleanupTimers()
		}
	}

	private fun cancelReleaseCleanupDeadline(): ReaderReleaseOnlyCleanupDeadlineStatus? {
		val slot = releaseCleanupDeadlineSlot ?: return null
		val disposition = try {
			val registration = slot.registration
			if (registration == null) {
				ReaderReleaseOnlyCleanupDeadlineStatus.CancellationRejected
			} else {
				registration.cancel()
				null
			}
		} catch (_: Throwable) {
			ReaderReleaseOnlyCleanupDeadlineStatus.CancellationThrew
		}
		val exactDisposition = if (
			disposition == null && slot.expirationObserved
		) {
			ReaderReleaseOnlyCleanupDeadlineStatus.CancellationRejected
		} else {
			disposition
		}
		if (exactDisposition == null) {
			if (releaseCleanupDeadlineSlot === slot) releaseCleanupDeadlineSlot = null
			return null
		}
		val cleanup = journal.releaseOnlyCleanup?.takeIf { it.key == slot.key }
		if (cleanup?.deadlineStatus == ReaderReleaseOnlyCleanupDeadlineStatus.Armed) {
			journal = journal.copy(
				releaseOnlyCleanup = cleanup.copy(deadlineStatus = exactDisposition),
				lastOutcome = ReaderTransitionOutcome.Failed(
					paige.navic.reader.ReaderTransitionFailureReason.CloseDrainTimeout,
					paige.navic.reader.ReaderTransitionRetryability.NonRetryable,
					paige.navic.reader.ReaderPresentationFrameOwner.Neutral
				),
				retryableTransition = null
			)
		}
		return exactDisposition
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

	private class ReleaseCleanupDeadlineSlot(
		val key: ReaderReleaseOnlyCleanupKey,
		val token: Long,
		val atMillis: Long,
		var registration: ReaderTransitionClockRegistration? = null,
		var expirationObserved: Boolean = false
	)

	private class ActivatedReleaseCleanupDeadlineSlot(
		val key: ReaderReleaseOnlyCleanupKey,
		val token: Long,
		var registration: ReaderTask6ReleaseOnlyTimerRegistration? = null
	)

	private companion object {
		const val ReleaseCleanupDeadlineMillis = 10_000L
		const val MaxReleaseAdmissionFailures = 32
		const val MaxRetiredTask6TimerCallbacks = 2
	}
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
	is ReaderTransitionFact.ReleaseCommandRejected,
	is ReaderTransitionFact.ReleaseCommandThrew,
	is ReaderTransitionFact.ReleasePortContractViolated,
	is ReaderTransitionFact.OwnedWorkCancellationCompleted,
	is ReaderTransitionFact.ReleaseLedgerAdmissionRejected,
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
	is ReaderTransitionFact.ReleaseCommandRejected,
	is ReaderTransitionFact.ReleaseCommandThrew,
	is ReaderTransitionFact.ReleasePortContractViolated,
	is ReaderTransitionFact.OwnedWorkCancellationCompleted,
	is ReaderTransitionFact.ReleaseLedgerAdmissionRejected,
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
	is ReaderTransitionFact.ReleaseCommandRejected -> ReaderTransitionFactKind.ReleaseCommandRejected
	is ReaderTransitionFact.ReleaseCommandThrew -> ReaderTransitionFactKind.ReleaseCommandThrew
	is ReaderTransitionFact.ReleasePortContractViolated -> ReaderTransitionFactKind.ReleasePortContractViolated
	is ReaderTransitionFact.OwnedWorkCancellationCompleted ->
		ReaderTransitionFactKind.OwnedWorkCancellationCompleted
	is ReaderTransitionFact.ReleaseLedgerAdmissionRejected ->
		ReaderTransitionFactKind.ReleaseLedgerAdmissionRejected
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
	is ReaderTransitionCommand.RequestResourceRelease,
	is ReaderTransitionCommand.ReleaseResource -> ReaderTransitionCommandKind.ReleaseResource
	is ReaderTransitionCommand.CancelOwnedWork -> ReaderTransitionCommandKind.CancelOwnedWork
}

private fun ReaderTransitionOutcome.kind(): ReaderTransitionOutcomeKind = when (this) {
	is ReaderTransitionOutcome.Succeeded -> ReaderTransitionOutcomeKind.Succeeded
	is ReaderTransitionOutcome.Failed -> ReaderTransitionOutcomeKind.Failed
	is ReaderTransitionOutcome.Cancelled -> ReaderTransitionOutcomeKind.Cancelled
	is ReaderTransitionOutcome.Deferred -> ReaderTransitionOutcomeKind.Deferred
}
