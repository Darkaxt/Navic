package paige.navic.ui.screens.reader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import paige.navic.reader.ReaderDiagnosticPresentation
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderReleaseOnlyCleanupKey
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.deadlinePolicy

/** One foreground-time deadline per common transaction, including its Foliate wait. */
internal class ReaderPresentationTransitionTimeout(
	private val scheduler: ReaderPageRelocationDispatchTimeoutScheduler = HandlerTimeoutScheduler(),
	private val nowMillis: () -> Long = SystemClock::uptimeMillis,
	private val timeoutMillis: Long = 10_000L,
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val onTimeout: (ReaderPresentationEvent.TimedOut) -> Boolean
) : ReaderTask6FactOnlyTimerPort {
	private class Pending(
		val token: ReaderPresentationToken,
		var remaining: Long,
		val activationToken: ReaderLegacySourceLocalOpaqueToken
	) {
		var startedAt = 0L
		var action: Runnable? = null
		var delivered = false
	}

	private var pending: Pending? = null
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private var restartPending: Pending? = null
	private val completedFrozen = linkedSetOf<ReaderLegacySourceLocalOpaqueToken>()

	private class CommandAttempt(
		val registration: ReaderTask6FactOnlyTimerRegistration,
		val onExpired: (ReaderTransitionFact.DeadlineExpired) -> Unit,
		var nextExpiresAtMillis: Long
	)
	private class CloseBudget(
		val registration: ReaderTask6ReleaseOnlyTimerRegistration,
		val onExpired: (ReaderReleaseOnlyCleanupKey) -> Unit
	) {
		var committed = false
		var delivered = false
	}
	private var commandDomain: ReaderLegacyPhysicalDomain? = null
	private var commandMode = false
	private var hostDisposed = false
	private var releaseOnlyCommitted = false
	private var nextCommandRegistrationId = 1L
	private var commandAttempt: CommandAttempt? = null
	private var closeBudget: CloseBudget? = null
	// Shared physical scheduler slot: command attempt OR committed close, never both.
	private var commandAction: Runnable? = null

	init { require(timeoutMillis > 0L) }

	internal val originalLegacyAdmissionRestored: Boolean
		get() = !hostDisposed && !commandMode && frozenDomain == null

	fun update(decision: ReaderPresentationDecision) {
		if (hostDisposed || commandMode || frozenDomain != null) return
		val token = decision.pendingTransitionToken
		if (token == null || decision.diagnosticPresentation is ReaderDiagnosticPresentation.Failure ||
			decision.lifecycle == ReaderPresentationLifecycleState.Destroyed
		) {
			cancel()
			return
		}
		if (pending?.token != token) {
			cancel()
			pending = Pending(token, timeoutMillis, tokenAllocator.allocate())
		}
		val attempt = checkNotNull(pending)
		if (decision.lifecycle != ReaderPresentationLifecycleState.Foreground) {
			pause(attempt)
			return
		}
		arm(attempt)
	}

	private fun arm(attempt: Pending) {
		if (attempt.action != null || attempt.delivered) return
		if (attempt.remaining == 0L) {
			deliver(attempt)
			return
		}
		lateinit var action: Runnable
		action = Runnable {
			if (pending !== attempt || attempt.action !== action) return@Runnable
			attempt.action = null
			attempt.remaining = 0L
			if (frozenDomain != null) {
				completedFrozen += attempt.activationToken
				return@Runnable
			}
			deliver(attempt)
		}
		attempt.startedAt = nowMillis()
		attempt.action = action
		if (!scheduler.postDelayed(action, attempt.remaining)) action.run()
	}

	private fun deliver(attempt: Pending) {
		attempt.delivered = true
		val accepted = onTimeout(ReaderPresentationEvent.TimedOut(attempt.token))
		// A missing common receipt can retry on the next host/authority update.
		if (pending === attempt && !accepted) attempt.delivered = false
	}

	private fun pause(attempt: Pending) {
		val action = attempt.action ?: return
		attempt.action = null
		attempt.remaining = (attempt.remaining - (nowMillis() - attempt.startedAt).coerceAtLeast(0L))
			.coerceAtLeast(0L)
		scheduler.removeCallbacks(action)
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = when {
		hostDisposed || commandMode -> ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		frozenDomain == null -> {
			frozenDomain = domain
			ReaderPortCommandResult.Accepted
		}
		frozenDomain == domain -> ReaderPortCommandResult.Accepted
		else -> ReaderPortCommandResult.Rejected(
			paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
		)
	}

	fun snapshotConnectedFrozenOwnership(): ReaderLegacyConnectedSourceInventory? {
		val domain = frozenDomain ?: return null
		if (hostDisposed || commandMode) return null
		return ReaderLegacyConnectedSourceInventory(ReaderLegacyInventorySource.DeadlineRegistration, domain, snapshotFrozenOwnership())
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> {
		val domain = frozenDomain ?: return emptyList()
		val attempt = pending ?: return emptyList()
		if (attempt.action == null && attempt.activationToken !in completedFrozen) {
			return emptyList()
		}
		return listOf(
			ReaderFrozenLegacyResource(
				freezeToken = domain.freezeToken,
				physicalIdentity = ReaderLegacyPhysicalIdentity(
					domain,
					ReaderLegacyInventorySource.DeadlineRegistration,
					attempt.activationToken
				),
				kind = paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration,
				binding = null,
				visibleOwner = null,
				origin = ReaderLegacyResourceOrigin.Pending,
				state = if (attempt.action == null) {
					ReaderLegacyResourceState.ReleaseRequested
				} else {
					ReaderLegacyResourceState.Registered
				},
				mayBeCommittedPredecessor = false
			)
		)
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		val domain = frozenDomain
		if (
			domain == null ||
			physicalIdentity.domain != domain ||
			physicalIdentity.source != ReaderLegacyInventorySource.DeadlineRegistration
		) return ReaderPortCommandResult.Rejected(
			paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
		)
		val attempt = pending
		if (attempt?.activationToken == physicalIdentity.sourceLocalToken) {
			pause(attempt)
			pending = null
			restartPending = attempt
			completedFrozen.remove(attempt.activationToken)
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		if (completedFrozen.remove(physicalIdentity.sourceLocalToken)) {
			onConfirmed(physicalIdentity)
			return ReaderPortCommandResult.Accepted
		}
		return ReaderPortCommandResult.Rejected(
			paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
		)
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		if (hostDisposed || commandMode || frozenDomain != domain) return ReaderPortCommandResult.Rejected(
			paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
		)
		val restart = restartPending
		restartPending = null
		completedFrozen.clear()
		frozenDomain = null
		commandDomain = domain
		if (restart != null) {
			pending = restart
			arm(restart)
		}
		return ReaderPortCommandResult.Accepted
	}

	/** Complete the original drained deadline source without restoring legacy work. No
	 * schedule, clock transfer, new token source, or successful expiry is created here. */
	fun finalizeActivatedHandoff(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult {
		if (hostDisposed || commandMode || frozenDomain != domain || pending?.action != null ||
			completedFrozen.isNotEmpty() || commandAttempt != null || closeBudget != null
		) return commandRejected()
		pending = null
		restartPending = null
		commandDomain = domain
		commandMode = true // Irreversible before any subsequent external scheduling boundary.
		frozenDomain = null
		return ReaderPortCommandResult.Accepted
	}

	/** View disposal ends ordinary work, not the permanent sink's exact cleanup budget.
	 * The same retained physical timer remains reachable through its installed port. */
	fun onHostDisposed() {
		if (hostDisposed) return
		hostDisposed = true
		if (!commandMode) {
			cancel()
			return
		}
		commandAttempt = null
		if (closeBudget?.committed != true) cancelCommandAction()
	}

	override fun bindBeforeWork(
		transitionId: ReaderTransitionId,
		onExpired: (ReaderTransitionFact.DeadlineExpired) -> Unit
	): ReaderTask6FactOnlyTimerRegistration? {
		val domain = commandDomain ?: return null
		if (hostDisposed || frozenDomain != null || releaseOnlyCommitted || pending != null || restartPending != null ||
			commandAttempt != null || closeBudget != null ||
			transitionId.readerSessionGeneration != domain.readerSessionGeneration ||
			nextCommandRegistrationId == Long.MAX_VALUE) return null
		val policy = transitionId.operation.deadlinePolicy()
		val now = nowMillis()
		val hardExpiry = expiryAfter(now, policy.hardMillis)
		val nextExpiry = policy.noProgressMillis?.let { minOf(hardExpiry, expiryAfter(now, it)) } ?: hardExpiry
		val registration = ReaderTask6FactOnlyTimerRegistration(
			ReaderTask6FactOnlyTimerRegistrationId(nextCommandRegistrationId++), transitionId,
			commandIdentity(domain), hardExpiry, policy.noProgressMillis, policy.noProgressMillis != null
		)
		val attempt = CommandAttempt(registration, onExpired, nextExpiry)
		commandMode = true
		commandAttempt = attempt
		try {
			if (scheduleCommand(nextExpiry) {
				if (commandAttempt === attempt) {
					commandAttempt = null // Consume before calling external FIFO fact delivery.
					attempt.onExpired(ReaderTransitionFact.DeadlineExpired(registration.transitionId))
				}
			}) return registration
		} catch (failure: Throwable) {
			if (commandAttempt === attempt) commandAttempt = null
			throw failure
		}
		if (commandAttempt === attempt) commandAttempt = null
		return null
	}

	override fun matchingProgress(
		registration: ReaderTask6FactOnlyTimerRegistration,
		nowMillis: Long
	): ReaderPortCommandResult {
		val attempt = commandAttempt
		val interval = registration.noProgressIntervalMillis
		if (attempt?.registration != registration || interval == null || nowMillis < 0L ||
			closeBudget?.committed == true) return commandRejected()
		cancelCommandAction()
		if (commandAttempt !== attempt) return commandRejected()
		attempt.nextExpiresAtMillis = minOf(registration.hardExpiresAtMillis, expiryAfter(nowMillis, interval))
		val scheduled = try {
			scheduleCommand(attempt.nextExpiresAtMillis) {
				if (commandAttempt === attempt) {
					commandAttempt = null
					attempt.onExpired(ReaderTransitionFact.DeadlineExpired(registration.transitionId))
				}
			}
		} catch (failure: Throwable) {
			if (commandAttempt === attempt) commandAttempt = null
			throw failure
		}
		if (scheduled) return ReaderPortCommandResult.Accepted
		if (commandAttempt === attempt) {
			commandAttempt = null
			attempt.onExpired(ReaderTransitionFact.DeadlineExpired(registration.transitionId))
		}
		return commandRejected()
	}

	override fun snapshotForTask7Transfer(
		registration: ReaderTask6FactOnlyTimerRegistration
	): ReaderTask6FactOnlyTimerTransferSnapshot? = commandAttempt
		?.takeIf { it.registration == registration }
		?.let { ReaderTask6FactOnlyTimerTransferSnapshot(registration, it.nextExpiresAtMillis) }

	override fun cancel(registration: ReaderTask6FactOnlyTimerRegistration): ReaderPortCommandResult {
		if (commandAttempt?.registration != registration) return commandRejected()
		commandAttempt = null
		cancelCommandAction()
		return ReaderPortCommandResult.Accepted
	}

	// PREPARE allocates exact custody only; the current attempt continues to own its clock.
	override fun bindReleaseOnlyCloseBudget(
		cleanupKey: ReaderReleaseOnlyCleanupKey,
		onExpired: (ReaderReleaseOnlyCleanupKey) -> Unit
	): ReaderTask6ReleaseOnlyTimerRegistration? {
		val domain = commandDomain ?: return null
		if (frozenDomain != null || releaseOnlyCommitted || pending != null || restartPending != null || closeBudget != null ||
			cleanupKey.readerSessionGeneration != domain.readerSessionGeneration ||
			nextCommandRegistrationId == Long.MAX_VALUE) return null
		val registration = ReaderTask6ReleaseOnlyTimerRegistration(
			ReaderTask6FactOnlyTimerRegistrationId(nextCommandRegistrationId++), cleanupKey,
			commandIdentity(domain), expiryAfter(nowMillis(), 2_000L)
		)
		commandMode = true // A prepared command registration never readmits legacy updates.
		closeBudget = CloseBudget(registration, onExpired)
		return registration
	}

	// COMMIT irreversibly fences the ordinary attempt before arming the sole close clock.
	override fun commitReleaseOnlyCloseBudget(
		registration: ReaderTask6ReleaseOnlyTimerRegistration
	): ReaderPortCommandResult {
		val budget = closeBudget
		if (budget?.registration != registration || budget.committed) return commandRejected()
		commandMode = true
		releaseOnlyCommitted = true
		budget.committed = true
		commandAttempt = null
		cancelCommandAction()
		if (closeBudget !== budget) return commandRejected()
		val scheduled = scheduleCommand(registration.expiresAtMillis) {
			if (closeBudget === budget && !budget.delivered) {
				budget.delivered = true
				budget.onExpired(registration.cleanupKey)
			}
		}
		// postDelayed is external: it may cancel this exact budget or synchronously
		// deliver its genuine expiry before returning schedule acceptance. Neither
		// permits an Armed publication; the expiry fact remains authoritative.
		return if (scheduled && closeBudget === budget && budget.registration == registration &&
			budget.committed && !budget.delivered && commandAction != null) {
			ReaderPortCommandResult.Accepted
		} else commandRejected()
	}

	override fun cancelReleaseOnlyCloseBudget(
		registration: ReaderTask6ReleaseOnlyTimerRegistration
	): ReaderPortCommandResult {
		val budget = closeBudget
		if (budget?.registration != registration) return commandRejected()
		closeBudget = null
		if (budget.committed) cancelCommandAction()
		return ReaderPortCommandResult.Accepted
	}

	private fun commandIdentity(domain: ReaderLegacyPhysicalDomain) = ReaderLegacyPhysicalIdentity(
		domain, ReaderLegacyInventorySource.DeadlineRegistration, tokenAllocator.allocate()
	)

	private fun scheduleCommand(expiresAtMillis: Long, onExpired: () -> Unit): Boolean {
		check(commandAction == null)
		lateinit var action: Runnable
		action = Runnable {
			if (commandAction !== action) return@Runnable
			commandAction = null
			onExpired()
		}
		commandAction = action
		val accepted = try {
			scheduler.postDelayed(action, (expiresAtMillis - nowMillis()).coerceAtLeast(0L))
		} catch (failure: Throwable) {
			if (commandAction === action) commandAction = null
			scheduler.removeCallbacks(action)
			throw failure
		}
		if (!accepted) {
			if (commandAction === action) commandAction = null
			scheduler.removeCallbacks(action)
		}
		return accepted
	}

	private fun cancelCommandAction() {
		val action = commandAction ?: return
		commandAction = null // Fence even a retired runnable invoked during removeCallbacks.
		scheduler.removeCallbacks(action)
	}

	private fun commandRejected() = ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
	private fun expiryAfter(now: Long, interval: Long): Long =
		if (now > Long.MAX_VALUE - interval) Long.MAX_VALUE else now + interval

	fun cancel() {
		commandAttempt = null
		closeBudget = null
		cancelCommandAction()
		val attempt = pending ?: return
		pending = null
		if (frozenDomain != null) completedFrozen += attempt.activationToken
		attempt.action?.let(scheduler::removeCallbacks)
	}
}

internal class HandlerTimeoutScheduler : ReaderPageRelocationDispatchTimeoutScheduler {
	private val handler = Handler(Looper.getMainLooper())
	override fun postDelayed(action: Runnable, delayMillis: Long): Boolean =
		handler.postDelayed(action, delayMillis)
	override fun removeCallbacks(action: Runnable) = handler.removeCallbacks(action)
}
