package paige.navic.ui.screens.reader

import android.view.View
import android.view.ViewGroup
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderInitialCommittedPresentationOrigin
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderPresentationEventOrigin
import paige.navic.reader.ReaderSemanticMutationStartEvidence
import paige.navic.reader.ReaderTransitionOperation
import java.lang.ref.WeakReference

/** A material installation, not the callback which happens to present it. Main-thread owned. */
internal class ReaderFramePhysicalOwnershipAdapter(
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator = ReaderLegacySourceLocalTokenAllocator(),
	private val physicalReturn: (() -> Unit) -> Unit = { it() },
	private val returnsInFlight: () -> Boolean = { false },
	private val capacity: Int = 16
) {
	internal class Lease internal constructor(
		internal val container: ViewGroup,
		internal val wrapper: View,
		internal val token: ReaderLegacySourceLocalOpaqueToken,
		internal val isCurrent: () -> Boolean,
		internal val commandCurrency: (() -> Boolean)? = null,
		internal val beforeCommandAdd: (() -> Unit)? = null
	) {
		internal var state = ReaderLegacyResourceState.Reserved
		internal var owned = true
		internal var addInvoked = false
		internal var operating = false
		internal var permanent = false
		internal var drainConfirmed = false
		internal var binding: ReaderPresentationBinding? = null
		internal var owner: ReaderPresentationFrameOwner? = null
		internal var visibleStillCurrent: (() -> Boolean)? = null
		internal var imported: ReaderImportedLegacyResourceRegistration? = null
		// Numeric initial layout belongs to this physical installation, not the retired
		// semantic execution. It is not allocation, decoration or display readiness.
		internal var initialResolvedLayout: ReaderNativeInitialResolvedLayout? = null
		// Temporary finite witness on this original initial lease. It never decides currency.
		internal var initialAdmissionCurrencyStage = ReaderInitialAdmissionStage.InstallationCurrency
		override fun toString(): String = "ReaderFrameInstallation(<redacted>)"
	}
	internal class RemovalResult(val completed: Boolean, val failure: Throwable? = null) {
		override fun toString(): String = "ReaderFrameRemovalResult(<redacted>)"
	}
	private val ownerThread = Thread.currentThread()
	private val leases = linkedSetOf<Lease>()
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private var finalized = false
	private var finalizedDomain: ReaderLegacyPhysicalDomain? = null
	private val historicalWrappers = mutableListOf<WeakReference<View>>()
	private var epoch = 0L
	val admissionOpen: Boolean get() = frozenDomain == null && !finalized
	fun captureEpoch(): Long = epoch
	fun admits(capturedEpoch: Long): Boolean = admissionOpen && epoch == capturedEpoch
	private fun checkThread() = check(Thread.currentThread() === ownerThread)
	fun reserve(container: ViewGroup, wrapper: View, isCurrent: () -> Boolean): Lease? {
		checkThread()
		if (!admissionOpen || leases.size >= capacity || leases.any { it.wrapper === wrapper && (it.owned || it.operating) }) return null
		return Lease(container, wrapper, tokenAllocator.allocate(), isCurrent).also {
			leases += it
			rememberWrapper(wrapper)
		}
	}
	private fun rememberWrapper(wrapper: View) {
		historicalWrappers.removeAll { it.get() == null }
		if (historicalWrappers.none { it.get() === wrapper }) historicalWrappers += WeakReference(wrapper)
	}
	/** Fresh command material only. This never reopens the finalized legacy admission. */
	fun reserveNeutralSemanticInstallation(
		container: ViewGroup,
		wrapper: View,
		candidate: ReaderTransitionGatewayCandidate,
		store: ReaderActivatedSessionSnapshotStore,
		origin: ReaderPresentationEventOrigin.SemanticCommand,
		context: ReaderSemanticMutationStartEvidence,
		isCurrent: () -> Boolean
	): Lease? {
		checkThread()
		val snapshot = store.snapshot ?: return null
		val neutral = snapshot.initialDecision?.origin as? ReaderInitialCommittedPresentationOrigin.Neutral ?: return null
		val reservation = snapshot.reservedNeutralBootstrap ?: return null
		val id = origin.transitionId
		if (!finalized || finalizedDomain?.readerSessionGeneration != neutral.readerSessionGeneration ||
			id.readerSessionGeneration != neutral.readerSessionGeneration || id.coordinatorEpoch != neutral.coordinatorEpoch ||
			id.operation != ReaderTransitionOperation.BootstrapNativePage ||
			id.expectedBinding !is ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial ||
			candidate.installationSource !== store) return null
		var reservedLease: Lease? = null
		fun currency(): Boolean {
			reservedLease?.initialAdmissionCurrencyStage = ReaderInitialAdmissionStage.InstallationCurrencyCandidateStore
			if (store.snapshot !== snapshot || !candidate.isSelected ||
				!store.ownsInstalledCandidate(candidate.epoch, id.readerSessionGeneration, id.coordinatorEpoch)) return false
			reservedLease?.initialAdmissionCurrencyStage = ReaderInitialAdmissionStage.InstallationCurrencyExecution
			return context.isExecutionCurrent(origin, reservation.handle)
		}
		historicalWrappers.removeAll { it.get() == null }
		if (!currency() || wrapper.parent != null || leases.size >= capacity ||
			historicalWrappers.any { it.get() === wrapper } || leases.any { it.owned || it.operating }) return null
		// Currency does not evaluate the caller predicate until the caller has retained its lease.
		return Lease(container, wrapper, tokenAllocator.allocate(), isCurrent, ::currency, context::mutationStarted).also {
			reservedLease = it
			leases += it
			rememberWrapper(wrapper)
		}
	}
	private fun admissionFor(lease: Lease): Boolean = lease.commandCurrency?.invoke() ?: admissionOpen
	fun mount(lease: Lease): Boolean {
		checkThread()
		if (lease !in leases || !lease.owned || lease.addInvoked || lease.operating || lease.permanent ||
			!admissionFor(lease) || !lease.isCurrent() || !admissionFor(lease) || !lease.owned || lease.permanent) return false
		lease.beforeCommandAdd?.invoke()
		if (!admissionFor(lease) || !lease.owned || lease.permanent) return false
		lease.addInvoked = true
		lease.operating = true
		try {
			physicalReturn {
				lease.container.addView(lease.wrapper, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
			}
			// Add acceptance is registration, never proof of a displayed predecessor.
			if (lease.owned && lease.state == ReaderLegacyResourceState.Reserved) lease.state = ReaderLegacyResourceState.Registered
		} finally { lease.operating = false }
		if (lease.commandCurrency != null && (!admissionFor(lease) || !lease.isCurrent() ||
			!admissionFor(lease) || lease.permanent || !lease.owned)) {
			retire(lease) // Exact captured cleanup; uncertain removal remains owned and retryable.
			return false
		}
		return true
	}
	fun retire(lease: Lease, permanent: Boolean = true): RemovalResult {
		checkThread()
		if (lease !in leases) return RemovalResult(!lease.owned)
		lease.initialResolvedLayout = null
		if (permanent) lease.permanent = true
		if (!lease.owned) return RemovalResult(true)
		if (lease.operating) return RemovalResult(false)
		lease.state = ReaderLegacyResourceState.ReleaseRequested
		lease.operating = true
		return try {
			// Always the captured original primitive, even if an earlier throw detached it.
			physicalReturn { lease.container.removeView(lease.wrapper) }
			lease.owned = false
			lease.state = ReaderLegacyResourceState.Released
			if (frozenDomain == null && lease.imported == null) leases.remove(lease)
			RemovalResult(true)
		} catch (failure: Throwable) {
			RemovalResult(false, failure)
		} finally { lease.operating = false }
	}
	fun hasInstallation(wrapper: View): Boolean = leases.any { it.wrapper === wrapper && (it.owned || it.operating) }
	fun freezeForTransitionActivation(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult {
		checkThread()
		if (finalized || (frozenDomain != null && frozenDomain != domain)) return invalid()
		if (frozenDomain == null) { epoch = Math.incrementExact(epoch); frozenDomain = domain }
		return ReaderPortCommandResult.Accepted
	}
	private fun identity(domain: ReaderLegacyPhysicalDomain, lease: Lease) = ReaderLegacyPhysicalIdentity(domain, ReaderLegacyInventorySource.FrameOrHandoff, lease.token)
	fun snapshotConnectedFrozenOwnership(): ReaderLegacyConnectedSourceInventory? {
		checkThread()
		val domain = frozenDomain ?: return null
		if (finalized || leases.any { it.operating } || returnsInFlight()) return null
		return ReaderLegacyConnectedSourceInventory(ReaderLegacyInventorySource.FrameOrHandoff, domain, snapshotFrozenOwnership())
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> {
		checkThread()
		val domain = frozenDomain ?: return emptyList()
		return leases.filterNot { it.drainConfirmed }.map { lease ->
			ReaderFrozenLegacyResource(domain.freezeToken, identity(domain, lease), ReaderTransitionResourceKind.FrameHandoff,
				lease.binding, lease.owner, if (lease.state == ReaderLegacyResourceState.Reserved) ReaderLegacyResourceOrigin.Pending else ReaderLegacyResourceOrigin.Owned,
				lease.state, lease.state == ReaderLegacyResourceState.Visible && lease.owner != null)
		}
	}
	fun drainFrozenOwnership(identity: ReaderLegacyPhysicalIdentity, onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit): ReaderPortCommandResult {
		checkThread()
		val domain = frozenDomain ?: return invalid()
		if (identity.domain != domain || identity.source != ReaderLegacyInventorySource.FrameOrHandoff) return invalid()
		val lease = leases.singleOrNull { it.token == identity.sourceLocalToken } ?: return invalid()
		if (lease.drainConfirmed || lease.operating || returnsInFlight()) return invalid()
		if (!retire(lease, permanent = false).completed) return invalid()
		lease.drainConfirmed = true
		onConfirmed(identity)
		return ReaderPortCommandResult.Accepted
	}
	fun restoreAfterTransitionActivation(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult {
		checkThread()
		if (frozenDomain != domain || returnsInFlight() || leases.any { it.operating || (it.owned && it.state == ReaderLegacyResourceState.ReleaseRequested) }) return invalid()
		// Replacement is irreversible. Only activation-cancelled, still-current slots can restart.
		for (lease in leases.toList()) {
			if (lease.permanent || !lease.isCurrent()) {
				if (lease.owned) return invalid()
				leases.remove(lease)
			} else if (!lease.owned) {
				if (leases.any { it !== lease && it.wrapper === lease.wrapper && it.owned }) return invalid()
				lease.operating = true
				try {
					physicalReturn { lease.container.addView(lease.wrapper, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
					lease.owned = true; lease.state = ReaderLegacyResourceState.Registered; lease.owner = null; lease.binding = null; lease.drainConfirmed = false
				} catch (_: Throwable) {
					lease.owned = true; lease.state = ReaderLegacyResourceState.Reserved; lease.drainConfirmed = false
					return invalid()
				} finally { lease.operating = false }
			}
		}
		frozenDomain = null
		return ReaderPortCommandResult.Accepted
	}
	internal class LiveInstallation internal constructor(
		private val source: ReaderFramePhysicalOwnershipAdapter,
		internal val lease: Lease,
		internal val engine: android.webkit.WebView,
		internal val epoch: Long,
		internal val currency: () -> Boolean,
		internal val displayed: () -> Boolean
	) {
		internal var admittedOwner: ReaderPresentationFrameOwner.LiveEngine? = null
		internal var admittedWitness: ReaderLiveEnginePhysicalWitness? = null
		// Completion belongs to this exact material source, not a rediscovered host.
		fun completeAcceptedPresentation(receipt: paige.navic.reader.ReaderPresentationEventReceipt): Boolean =
			source.completeLivePresentation(this, receipt)
		override fun toString(): String = "ReaderFrameLiveInstallation(<redacted>)"
	}
	fun captureLiveInstallation(lease: Lease, engine: android.webkit.WebView,
		currency: () -> Boolean, displayed: () -> Boolean): LiveInstallation? {
		checkThread()
		if (!admissionOpen || lease !in leases || !lease.owned || lease.operating || !lease.isCurrent() ||
			lease.state !in setOf(ReaderLegacyResourceState.Registered, ReaderLegacyResourceState.Visible) ||
			!currency() || !physicallyContains(lease, engine)) return null
		return LiveInstallation(this, lease, engine, epoch, currency, displayed)
	}
	private fun physicallyContains(lease: Lease, engine: android.webkit.WebView): Boolean {
		if (lease.wrapper.parent !== lease.container || !lease.wrapper.isAttachedToWindow || !engine.isAttachedToWindow) return false
		var current: View = engine
		while (current !== lease.wrapper) current = current.parent as? View ?: return false
		return true
	}
	private fun livePresentationFactsMatch(installation: LiveInstallation, witness: ReaderLiveEnginePhysicalWitness): Boolean {
		val lease = installation.lease
		return admits(installation.epoch) && lease in leases && lease.owned && !lease.operating && !lease.permanent &&
			lease.state in setOf(ReaderLegacyResourceState.Registered, ReaderLegacyResourceState.Visible) &&
			lease.isCurrent() && installation.currency() && physicallyContains(lease, installation.engine) &&
			witness.matches(installation.engine) && installation.displayed()
	}
	// Admission is a precondition for sending the semantic event. It must not make
	// a Registered mount importable while that event can still be rejected/cancelled.
	fun acceptLivePresentation(installation: LiveInstallation, witness: ReaderLiveEnginePhysicalWitness,
		owner: ReaderPresentationFrameOwner.LiveEngine): Boolean {
		checkThread()
		installation.admittedOwner = null; installation.admittedWitness = null
		if (!livePresentationFactsMatch(installation, witness)) return false
		installation.admittedOwner = owner; installation.admittedWitness = witness
		return true
	}
	private fun completeLivePresentation(installation: LiveInstallation,
		receipt: paige.navic.reader.ReaderPresentationEventReceipt): Boolean {
		checkThread()
		val event = receipt.event as? paige.navic.reader.ReaderPresentationEvent.LiveEngineExposureCommitted ?: return false
		val owner = installation.admittedOwner ?: return false
		val witness = installation.admittedWitness ?: return false
		val decision = paige.navic.reader.readerPresentationDecision(receipt.postState)
		if (!receipt.authorizes(event) || event.proof != owner.proof || decision.frameOwner != owner ||
			(decision.authority as? paige.navic.reader.ReaderPresentationAuthority.LiveEngineExposed)?.frame != owner ||
			!livePresentationFactsMatch(installation, witness)) return false
		val lease = installation.lease
		lease.binding = owner.proof.binding; lease.owner = owner
		lease.visibleStillCurrent = { lease.isCurrent() && physicallyContains(lease, installation.engine) && installation.displayed() }
		lease.state = ReaderLegacyResourceState.Visible
		installation.admittedOwner = null; installation.admittedWitness = null
		return true
	}
	internal fun frozenIdentities(domain: ReaderLegacyPhysicalDomain): Set<ReaderLegacyPhysicalIdentity> =
		if (frozenDomain == domain) leases.map { identity(domain, it) }.toSet() else emptySet()
	fun finalizeActivatedHandoff(domain: ReaderLegacyPhysicalDomain,
		selected: ReaderImportedLegacyResourceRegistration?, confirmedRetirements: Set<ReaderLegacyPhysicalIdentity>): ReaderPortCommandResult {
		checkThread()
		if (frozenDomain != domain || returnsInFlight() || leases.any { it.operating }) return invalid()
		if (selected != null && (selected.physicalIdentity.domain != domain ||
			selected.physicalIdentity.source != ReaderLegacyInventorySource.FrameOrHandoff ||
			selected.registration.key.kind != ReaderTransitionResourceKind.FrameHandoff)) return invalid()
		val chosen = selected?.let { import -> leases.singleOrNull { identity(domain, it) == import.physicalIdentity } ?: return invalid() }
		if (chosen != null && (!chosen.owned || chosen.permanent || chosen.state != ReaderLegacyResourceState.Visible ||
			chosen.owner !is ReaderPresentationFrameOwner.LiveEngine || chosen.binding != (chosen.owner as ReaderPresentationFrameOwner.LiveEngine).proof.binding ||
			chosen.visibleStillCurrent?.invoke() != true || chosen.drainConfirmed)) return invalid()
		val retired = leases.filterNot { it === chosen }
		if (confirmedRetirements != retired.map { identity(domain, it) }.toSet() ||
			retired.any { it.owned || !it.drainConfirmed || it.state != ReaderLegacyResourceState.Released }) return invalid()
		leases.clear()
		if (chosen != null) { chosen.imported = selected; leases += chosen }
		finalizedDomain = domain
		finalized = true; frozenDomain = null; epoch = Math.incrementExact(epoch)
		return ReaderPortCommandResult.Accepted
	}
	fun releaseLegacy(command: ReaderTransitionCommand.ReleaseResource, imported: ReaderImportedLegacyResourceRegistration,
		onConfirmed: (ReaderLegacyPhysicalIdentity, ReaderTransitionFact.ResourceReleased) -> Unit): ReaderPortCommandResult {
		checkThread()
		val lease = leases.singleOrNull { it.imported == imported } ?: return invalid()
		if (command.identity.registration != imported.registration || !lease.owned || lease.operating || returnsInFlight()) return invalid()
		val result = retire(lease)
		if (!result.completed) return invalid()
		leases.remove(lease)
		onConfirmed(imported.physicalIdentity, ReaderTransitionFact.ResourceReleased(command.identity))
		return ReaderPortCommandResult.Accepted
	}
	private fun invalid() = ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)
}
