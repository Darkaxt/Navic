package paige.navic.ui.screens.reader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import paige.navic.reader.ReaderDiagnosticPresentation
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationToken

/** One foreground-time deadline per common transaction, including its Foliate wait. */
internal class ReaderPresentationTransitionTimeout(
	private val scheduler: ReaderPageRelocationDispatchTimeoutScheduler = HandlerTimeoutScheduler(),
	private val nowMillis: () -> Long = SystemClock::uptimeMillis,
	private val timeoutMillis: Long = 10_000L,
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val onTimeout: (ReaderPresentationEvent.TimedOut) -> Boolean
) {
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

	init { require(timeoutMillis > 0L) }

	fun update(decision: ReaderPresentationDecision) {
		if (frozenDomain != null) return
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
		frozenDomain == null -> {
			frozenDomain = domain
			ReaderPortCommandResult.Accepted
		}
		frozenDomain == domain -> ReaderPortCommandResult.Accepted
		else -> ReaderPortCommandResult.Rejected(
			paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
		)
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
		if (frozenDomain != domain) return ReaderPortCommandResult.Rejected(
			paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource
		)
		val restart = restartPending
		restartPending = null
		completedFrozen.clear()
		frozenDomain = null
		if (restart != null) {
			pending = restart
			arm(restart)
		}
		return ReaderPortCommandResult.Accepted
	}

	fun cancel() {
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
