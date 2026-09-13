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
	val lastOutcome: ReaderTransitionOutcomeKind?
) {
	companion object {
		const val MaxShadowPredictions = 32
	}
}

internal class ReaderResumableTransitionCoordinator(
	private val ports: ReaderResumableTransitionPorts,
	private val mode: ReaderTransitionMode,
	private var journal: ReaderTransitionJournal,
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

	fun enqueue(fact: ReaderTransitionFact) {
		check(Looper.myLooper() == Looper.getMainLooper()) {
			"Reader transition facts must be enqueued on the main thread"
		}
		mailbox.addLast(fact)
		if (!advancing) advance()
	}

	fun snapshot(): ReaderTransitionCoordinatorSnapshot = ReaderTransitionCoordinatorSnapshot(
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
		lastOutcome = journal.lastOutcome?.kind()
	)

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
				val reduction = journal.reduce(fact, nowMillis)

				// This assignment is the coordinator's publication barrier: callbacks may run
				// synchronously from either deadline registration or command issuance below.
				journal = reduction.state
				persistActiveRegistration()
				reconcileDeadline(nowMillis, before, fact, classification)
				recordShadowPrediction(fact, classification, before, reduction.commands)

				if (mode == ReaderTransitionMode.Active) {
					reduction.commands.forEach { command -> ports.issue(command, ::enqueue) }
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
