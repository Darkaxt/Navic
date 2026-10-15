package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationPublicationIdentity
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.readerPresentationDecision

internal const val ReaderPresentationLifecyclePendingEventLimit = 14

internal data class ReaderPresentationLifecycleReceiptAdmission(
	val event: ReaderPresentationEvent.Lifecycle,
	val receipt: ReaderPresentationEventReceipt
)

internal class ReaderPresentationLifecycleDelivery(
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator(),
	private val activatedSessionStore: ReaderActivatedSessionSnapshotStore =
		ReaderActivatedSessionSnapshotStore()
) {
	// Route selection is the existing immutable installation authority, including
	// ReleaseOnly. Physical callback returns remain owned independently of this gate.
	internal val legacyConsequencesOpen: Boolean
		get() = activatedSessionStore.legacyConsequencesOpen

	private class PendingSafetyFact(
		val snapshot: ReaderActivatedSessionSnapshot,
		val fact: ReaderTask6LifecycleSafetyFact,
		var invokedWithoutResult: Boolean = false
	)
	private val pendingSafetyFacts = ArrayDeque<PendingSafetyFact>()
	private var safetyDeliverySequence = 0L
	private var safetyDeliveryInProgress = false
	// Logical terminal ingress is distinct from physical registered-root removal.
	// This monotonic fence does not select installation authority or reopen on restore.
	private var safetyIngressClosed = false

	private fun observeInstalledSafetyFact(
		snapshot: ReaderActivatedSessionSnapshot,
		kind: ReaderTask6LifecycleSafetyKind
	) {
		if (!snapshot.commandEgressOpen || snapshot.portAuthority == null) return
		safetyDeliverySequence = Math.incrementExact(safetyDeliverySequence)
		pendingSafetyFacts.addLast(PendingSafetyFact(snapshot,
			ReaderTask6LifecycleSafetyFact(safetyDeliverySequence, kind)))
	}

	private fun retryInstalledSafetyFacts() {
		if (safetyDeliveryInProgress) return
		val invocation = beginRegisteredRootReturn() ?: return
		safetyDeliveryInProgress = true
		try {
			while (pendingSafetyFacts.isNotEmpty()) {
				val pending = pendingSafetyFacts.first()
				val current = activatedSessionStore.snapshot
				if (current !== pending.snapshot || !current.commandEgressOpen) return
				val port = when (val authority = current.portAuthority) {
					is ReaderActivatedSessionPortAuthority.Production -> authority.ports.lifecycleFacts
					is ReaderActivatedSessionPortAuthority.Test -> authority.ports.lifecycleFacts
					null -> null
				} ?: return
				if (pending.invokedWithoutResult) return
				val recipient = beginRegisteredRootReturn() ?: return
				pending.invokedWithoutResult = true
				val result = try {
					port.enqueueSafetyFact(pending.fact)
				} catch (_: Throwable) {
					// Unknown acceptance cannot be guessed or replayed. Keep the exact
					// unresolved head; later observations cannot overtake it.
					return
				} finally { endPhysicalDelivery(recipient) }
				when (result) {
					ReaderPortCommandResult.Accepted -> pendingSafetyFacts.removeFirst()
					is ReaderPortCommandResult.Rejected -> {
						pending.invokedWithoutResult = false
						return
					}
				}
				// A recipient/finalization/physical confirmation may reenter installation.
				if (activatedSessionStore.snapshot !== current) return
			}
		} finally {
			safetyDeliveryInProgress = false
			endPhysicalDelivery(invocation)
		}
	}

	private data class RestartSnapshot(
		val expectedReaderSessionGeneration: Long?,
		val expectedPublicationIdentity: ReaderPresentationPublicationIdentity?,
		val minimumEventSequence: Long,
		val acknowledgedVersion: ReaderPresentationReceiptVersion?,
		val initialLifecycle: ReaderPresentationLifecycleState,
		val acknowledgedLifecycle: ReaderPresentationLifecycleState?,
		val observedWindowVisible: Boolean?,
		val visibilityLossRequired: Boolean,
		val visibilityConfirmationRequired: Boolean,
		val pendingMemoryPressure: Set<ReaderPresentationLifecycleEvent>,
		val rendererLossPending: Boolean,
		val publicationClosePending: Boolean,
		val publicationCloseAcknowledged: Boolean
	)

	private var expectedReaderSessionGeneration: Long? = null
	private var expectedPublicationIdentity: ReaderPresentationPublicationIdentity? = null
	private var minimumEventSequence: Long = 0L
	private var acknowledgedVersion: ReaderPresentationReceiptVersion? = null
	private var initialLifecycle: ReaderPresentationLifecycleState =
		ReaderPresentationLifecycleState.Foreground
	var acknowledgedLifecycle: ReaderPresentationLifecycleState? = null
		private set
	private var observedWindowVisible: Boolean? = null
	private var visibilityLossRequired = false
	private var visibilityConfirmationRequired = false
	private val pendingMemoryPressure = linkedSetOf<ReaderPresentationLifecycleEvent>()
	private var rendererLossPending = false
	private var publicationClosePending = false
	private var publicationCloseAcknowledged = false
	private var retryInProgress = false
	private val physicalOwners = ReaderExactPhysicalOwnerRegistry(
		ReaderLegacyInventorySource.LifecycleDelivery, tokenAllocator
	)
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private var legacyFinalized = false
	private var restartSnapshot: RestartSnapshot? = null
	private var physicalDepth = 0
	private var settlingReturns = false
	private val settledReturnTails = linkedSetOf<ReaderExactPhysicalOwnerRegistry.Owner>()
	private var admissionEpoch = 0L
	private var registeredRootsClosed = false
	private var restoringRegisteredRoots = false
	private var restorationRefrozen = false
	private var rootDrainDomain: ReaderLegacyPhysicalDomain? = null
	private val restorationRootTokens = linkedSetOf<ReaderLegacySourceLocalOpaqueToken>()
	private val registeredRoots = linkedSetOf<RegisteredRoot>()

	// The physical framework producer exists before a reader session can be validated.
	// Its opaque record joins this SAME registry; only freeze binds a session/domain.
	internal inner class RegisteredRoot internal constructor(
		private val registerPhysical: () -> Unit,
		private val unregisterPhysical: () -> Unit,
		private val isValid: () -> Boolean
	) {
		private var physicalOwner: ReaderExactPhysicalOwnerRegistry.Owner? = null
		private var registrationInProgress = false
		private var registrationUncertain = false
		private var removalInProgress = false
		private var permanentlyRemoved = false
		private var removedDomain: ReaderLegacyPhysicalDomain? = null

		fun register(): Boolean {
			if (!registeredRootAdmissionOpen || !isValid() || permanentlyRemoved) return false
			return registerInDomain(null)
		}

		// A removed predecessor cannot restart while activation is reversible. Once its
		// original legacy custody is permanently retired, this exact producer may obtain
		// a distinct safety-only installation from the actually selected successor.
		fun registerInstalledSafetyIngress(): Boolean {
			if (activatedSessionStore.snapshot == null || registeredRootsClosed ||
				permanentlyRemoved || !isValid()) return false
			if (legacyFinalized) {
				if (currentInstalledSafetySnapshot() == null) return false
			} else if (removedDomain != null) return false
			return registerInDomain(null)
		}

		private fun registerInDomain(domain: ReaderLegacyPhysicalDomain?): Boolean {
			if (physicalOwner != null) {
				if (registrationUncertain) return false
				if (domain != null) removedDomain = null
				return true
			}
			if (registrationInProgress || removalInProgress || permanentlyRemoved ||
				registeredRootsClosed || !isValid()) return false
			val invocation = beginRegisteredRootReturn() ?: return false
			registrationInProgress = true
			return try {
				val descriptor = ReaderExactPhysicalOwnerDescriptor(
					kind = ReaderTransitionResourceKind.CallbackRegistration,
					state = ReaderLegacyResourceState.Reserved
				)
				// Reserved names an invoked registration whose acceptance has not returned,
				// not a dormant producer. Synchronous delivery proves acceptance earlier.
				val acceptedOwner = admitRootOwner(descriptor) {
					rootDrainDomain = frozenDomain
					remove()
				} ?: return false
				physicalOwner = acceptedOwner
				registerPhysical()
				// Removal may have reentered from synchronous framework replay. Never
				// resurrect that registration merely because add/register then returned.
				if (physicalOwner === acceptedOwner) {
					physicalOwners.updateState(acceptedOwner, ReaderLegacyResourceState.Registered)
					if (domain != null) removedDomain = null
				}
				true
			} catch (failure: Throwable) {
				// A callback proves the registration was accepted. Without callback/return
				// proof, keep a Reserved possible installation and require exact removal
				// before retry; never report a successful restore or register it twice.
				physicalOwner?.let { registrationUncertain = it.state != ReaderLegacyResourceState.Registered }
				throw failure
			} finally {
				registrationInProgress = false
				endPhysicalDelivery(invocation)
			}
		}

		fun callback(
			onPhysicalReturn: () -> Unit = {},
			installedSafetyObservation: (() -> Unit)? = null,
			action: (admission: Long) -> Unit
		) {
			// An already accepted framework callback has a real return even if frozen.
			val invocation = beginRegisteredRootReturn() ?: return
			val admission = capturePhysicalAdmission()
			try {
				physicalOwner?.let { physicalOwners.updateState(it, ReaderLegacyResourceState.Registered) }
				if (installedSafetyObservation != null && physicalOwner != null &&
					!permanentlyRemoved && isValid() && activatedSessionStore.snapshot != null) {
					installedSafetyObservation()
				} else if (registeredRootAdmits(admission) && !permanentlyRemoved && isValid()) action(admission)
			} finally {
				try {
					onPhysicalReturn()
				} finally {
					endPhysicalDelivery(invocation)
				}
			}
		}

		fun remove(permanent: Boolean = false): Boolean {
			if (permanent) { permanentlyRemoved = true; removedDomain = null }
			val owner = physicalOwner ?: return true
			if (removalInProgress) return false
			val invocation = beginRegisteredRootReturn() ?: return false
			removalInProgress = true
			return try {
				unregisterPhysical()
				physicalOwner = null
				registrationUncertain = false
				if (!permanentlyRemoved && !registeredRootsClosed && isValid()) removedDomain = frozenDomain
				physicalOwners.updateState(owner, ReaderLegacyResourceState.Released)
				settlePhysicalOwner(owner)
				true
			} catch (_: Throwable) {
				// Keep the exact possibly-installed record. Registry rejection rolls back
				// this cancellation request, so a deterministic retry can remove it.
				false
			} finally {
				removalInProgress = false
				endPhysicalDelivery(invocation)
			}
		}

		internal fun restore(domain: ReaderLegacyPhysicalDomain): Boolean {
			if (removedDomain != domain) return true
			if (permanentlyRemoved || registeredRootsClosed || !isValid()) {
				removedDomain = null
				return true
			}
			return registerInDomain(domain)
		}
	}

	internal fun registeredRoot(
		registerPhysical: () -> Unit,
		unregisterPhysical: () -> Unit,
		isValid: () -> Boolean
	): RegisteredRoot = RegisteredRoot(registerPhysical, unregisterPhysical, isValid).also(registeredRoots::add)

	internal fun closeRegisteredRoots() {
		registeredRootsClosed = true
	}

	internal val originalLegacyAdmissionRestored: Boolean
		get() = registeredRootAdmissionOpen && !physicalOwners.isFrozen &&
			!restoringRegisteredRoots && !hasPhysicalReturnsInFlight

	internal val registeredRootAdmissionOpen: Boolean
		get() = frozenDomain == null && !registeredRootsClosed

	internal fun registeredRootAdmits(epoch: Long): Boolean =
		registeredRootAdmissionOpen && admissionEpoch == epoch

	private fun currentInstalledSafetySnapshot(): ReaderActivatedSessionSnapshot? {
		val snapshot = activatedSessionStore.snapshot ?: return null
		if (!snapshot.commandEgressOpen) return null
		val gateway = when (val authority = snapshot.portAuthority) {
			is ReaderActivatedSessionPortAuthority.Production -> authority.ports.gateway
			is ReaderActivatedSessionPortAuthority.Test -> authority.ports.gateway
			null -> null
		} as? ReaderSnapshotBackedActivatedGatewayPort ?: return null
		val candidate = gateway.candidate
		return snapshot.takeIf { candidate.isSelected && candidate.installationSource === activatedSessionStore &&
			candidate.readerSessionGeneration == frozenDomain?.readerSessionGeneration &&
			activatedSessionStore.ownsInstalledCandidate(candidate.epoch, candidate.readerSessionGeneration, candidate.coordinatorEpoch) }
	}

	private fun admitRootOwner(
		descriptor: ReaderExactPhysicalOwnerDescriptor,
		cancelPhysical: (() -> Boolean)? = null
	): ReaderExactPhysicalOwnerRegistry.Owner? {
		if (legacyFinalized) {
			val domain = frozenDomain ?: return null
			val snapshot = activatedSessionStore.snapshot ?: return null
			return physicalOwners.admitInstalledPhysicalOwner(domain, activatedSessionStore, snapshot, descriptor, cancelPhysical)
		}
		val owner = frozenDomain?.let { domain ->
			physicalOwners.admitLateDiscoveredDuringFrozenEpoch(domain, descriptor, cancelPhysical)
		} ?: physicalOwners.admit(descriptor, cancelPhysical)
		if (restoringRegisteredRoots && owner != null) restorationRootTokens += owner.token
		return owner
	}

	// Registration/removal/callback and the enclosing source return share depth with
	// existing dispatcher/effect returns. No inner return can confirm an outer owner.
	internal fun beginRegisteredRootReturn(): ReaderExactPhysicalOwnerRegistry.Owner? {
		val owner = admitRootOwner(ReaderExactPhysicalOwnerDescriptor(
			kind = ReaderTransitionResourceKind.CallbackRegistration,
			state = ReaderLegacyResourceState.Running
		)) ?: return null
		physicalDepth++
		return owner
	}


	// A real dispatcher queue entry, separate from its reversible logical projection.
	internal class PendingHostEffectOwner internal constructor() {
		internal var physicalOwner: ReaderExactPhysicalOwnerRegistry.Owner? = null
	}
	private val pendingHostEffectOwners = linkedSetOf<PendingHostEffectOwner>()

	internal val physicalDeliveryAdmissionOpen: Boolean
		get() = legacyConsequencesOpen && frozenDomain == null && expectedReaderSessionGeneration != null

	// Derived physical return fact only; never a permission or removal confirmation.
	internal val hasPhysicalReturnsInFlight: Boolean
		get() = physicalDepth != 0 || settlingReturns

	internal fun capturePhysicalAdmission(): Long = admissionEpoch

	internal fun admitsPhysicalDelivery(epoch: Long): Boolean =
		physicalDeliveryAdmissionOpen && admissionEpoch == epoch

	internal fun beginPhysicalDelivery(): ReaderExactPhysicalOwnerRegistry.Owner? {
		if (!physicalDeliveryAdmissionOpen) return null
		val owner = physicalOwners.admit(ReaderExactPhysicalOwnerDescriptor(
			kind = ReaderTransitionResourceKind.CallbackRegistration,
			state = ReaderLegacyResourceState.Running
		)) ?: return null
		physicalDepth++
		return owner
	}

	internal fun endPhysicalDelivery(owner: ReaderExactPhysicalOwnerRegistry.Owner) {
		physicalOwners.updateState(owner, ReaderLegacyResourceState.Released)
		settledReturnTails += owner
		check(physicalDepth > 0)
		physicalDepth--
		if (physicalDepth != 0) return
		// Accepted callback return tails belong to their enclosing dispatcher/retry.
		// Detach the batch before confirmations, which can themselves reenter.
		val settled = settledReturnTails.toList()
		settledReturnTails.clear()
		settlingReturns = true
		try {
			settled.forEach { physicalOwners.updateState(it, ReaderLegacyResourceState.Released) }
			physicalOwners.complete(settled)
		} finally {
			settlingReturns = false
		}
	}

	private fun settlePhysicalOwner(owner: ReaderExactPhysicalOwnerRegistry.Owner) {
		if (physicalDepth == 0) physicalOwners.complete(owner) else settledReturnTails += owner
	}

	internal fun registerPendingHostEffect(): PendingHostEffectOwner? {
		if (!physicalDeliveryAdmissionOpen) return null
		return PendingHostEffectOwner().also { pending ->
			pendingHostEffectOwners += pending
			registerPendingHostEffectPhysicalOwner(pending)
		}
	}

	private fun registerPendingHostEffectPhysicalOwner(pending: PendingHostEffectOwner) {
		pending.physicalOwner = physicalOwners.admit(
			ReaderExactPhysicalOwnerDescriptor(
				kind = ReaderTransitionResourceKind.CallbackRegistration,
				origin = ReaderLegacyResourceOrigin.Pending,
				state = ReaderLegacyResourceState.Registered
			),
			cancelPhysical = {
				val owner = pending.physicalOwner
				pending.physicalOwner = null
				owner?.let(::settlePhysicalOwner)
				true // Parks unstarted projection demand; never claims a running tail returned.
			}
		)
	}

	internal fun completePendingHostEffect(pending: PendingHostEffectOwner) {
		pendingHostEffectOwners.remove(pending)
		val owner = pending.physicalOwner
		pending.physicalOwner = null
		owner?.let(::settlePhysicalOwner)
	}

	// Consumption is logical accounting, not permission to publish the returned receipt.
	internal fun consumeReturnedLifecycleReceipt(
		event: ReaderPresentationEvent.Lifecycle,
		receipt: ReaderPresentationEventReceipt
	): Boolean {
		if (acknowledgedVersion == receipt.version && receipt.event == event) return true
		if (event.event != nextPendingEvent() || !receiptAcknowledges(event, receipt)) return false
		acknowledge(event.event, receipt)
		if (frozenDomain != null) restartSnapshot = captureRestartSnapshot()
		return true
	}

	val pendingEventCount: Int
		get() = visibilityPendingEventCount() +
			pendingMemoryPressure.size +
			(if (rendererLossPending) 1 else 0) +
			(if (publicationClosePending) 1 else 0)

	val hasPendingRendererLoss: Boolean
		get() = rendererLossPending

	fun reset(
		version: ReaderPresentationReceiptVersion,
		observedWindowVisible: Boolean?,
		initialLifecycle: ReaderPresentationLifecycleState =
			ReaderPresentationLifecycleState.Foreground
	) {
		check(frozenDomain == null && !settlingReturns) { "Lifecycle delivery reset is frozen" }
		// Host replacement may reenter from a recipient/effect. Replace logical state,
		// fence the old admission epoch, but retain every real on-stack return owner.
		admissionEpoch++
		expectedReaderSessionGeneration = version.readerSessionGeneration
		expectedPublicationIdentity = null
		minimumEventSequence = version.eventSequence
		acknowledgedVersion = null
		this.initialLifecycle = initialLifecycle
		acknowledgedLifecycle = null
		this.observedWindowVisible = observedWindowVisible
		visibilityLossRequired = observedWindowVisible == false
		visibilityConfirmationRequired = observedWindowVisible != null
		pendingMemoryPressure.clear()
		rendererLossPending = false
		publicationClosePending = false
		publicationCloseAcknowledged = false
	}

	fun bindPublication(identity: ReaderPresentationPublicationIdentity): Boolean {
		if (
			frozenDomain != null ||
			expectedReaderSessionGeneration == null ||
			publicationCloseAcknowledged
		) return false
		val expected = expectedPublicationIdentity
		if (expected != null && expected != identity) return false
		expectedPublicationIdentity = identity
		return true
	}

	fun advanceComposeVersion(version: ReaderPresentationReceiptVersion): Boolean {
		if (frozenDomain != null) return false
		val expectedSession = expectedReaderSessionGeneration ?: return false
		val expectedPublication = expectedPublicationIdentity ?: return false
		if (
			version.readerSessionGeneration != expectedSession ||
			version.publicationIdentity != expectedPublication
		) return false
		minimumEventSequence = maxOf(minimumEventSequence, version.eventSequence)
		return true
	}

	fun observe(event: ReaderPresentationLifecycleEvent) {
		val safetyIngressWasClosed = safetyIngressClosed
		if (event == ReaderPresentationLifecycleEvent.PublicationClosed) {
			closeRegisteredRoots()
			safetyIngressClosed = true
		}
		val installed = activatedSessionStore.snapshot
		if (installed != null) {
			// Retain and retry the exact queued close (and its predecessors), but never
			// append an ordinary fact or duplicate terminal fact after logical close.
			if (safetyIngressWasClosed) return
			val kind = when (event) {
				ReaderPresentationLifecycleEvent.VisibilityLost -> ReaderTask6LifecycleSafetyKind.VisibilityLost
				ReaderPresentationLifecycleEvent.RendererLost -> ReaderTask6LifecycleSafetyKind.RendererUnavailable
				ReaderPresentationLifecycleEvent.HostDetached -> ReaderTask6LifecycleSafetyKind.HostDetached
				ReaderPresentationLifecycleEvent.WebViewDetached -> ReaderTask6LifecycleSafetyKind.WebViewDetached
				ReaderPresentationLifecycleEvent.PublicationClosed -> ReaderTask6LifecycleSafetyKind.PublicationClosed
				ReaderPresentationLifecycleEvent.VisibilityRestored,
				is ReaderPresentationLifecycleEvent.RunningMemoryPressure,
				is ReaderPresentationLifecycleEvent.BackgroundMemoryPressure -> null
			}
			if (kind != null) observeInstalledSafetyFact(installed, kind)
			return
		}
		if (frozenDomain != null || publicationCloseAcknowledged) return
		if (event == ReaderPresentationLifecycleEvent.PublicationClosed) {
			publicationClosePending = true
			return
		}
		if (publicationClosePending) return
		when (event) {
			ReaderPresentationLifecycleEvent.VisibilityLost -> {
				observedWindowVisible = false
				if (acknowledgedLifecycle == ReaderPresentationLifecycleState.Background) {
					visibilityLossRequired = false
					visibilityConfirmationRequired = false
				} else {
					visibilityLossRequired = true
					visibilityConfirmationRequired = true
				}
			}
			ReaderPresentationLifecycleEvent.VisibilityRestored -> {
				observedWindowVisible = true
				visibilityConfirmationRequired = visibilityLossRequired ||
					acknowledgedLifecycle != ReaderPresentationLifecycleState.Foreground
			}
			is ReaderPresentationLifecycleEvent.RunningMemoryPressure,
			is ReaderPresentationLifecycleEvent.BackgroundMemoryPressure ->
				pendingMemoryPressure += event
			ReaderPresentationLifecycleEvent.HostDetached,
			ReaderPresentationLifecycleEvent.WebViewDetached -> Unit
			ReaderPresentationLifecycleEvent.RendererLost -> rendererLossPending = true
			ReaderPresentationLifecycleEvent.PublicationClosed -> error("Handled above")
		}
		check(pendingEventCount <= ReaderPresentationLifecyclePendingEventLimit)
	}

	fun retry(
		dispatch: (ReaderPresentationEvent.Lifecycle) -> ReaderPresentationEventReceipt?
	): ReaderPresentationEventReceipt? {
		if (!legacyConsequencesOpen) { retryInstalledSafetyFacts(); return null }
		if (frozenDomain != null || retryInProgress || expectedPublicationIdentity == null) return null
		val lifecycleEvent = nextPendingEvent() ?: return null
		val event = ReaderPresentationEvent.Lifecycle(lifecycleEvent)
		val owner = beginPhysicalDelivery() ?: return null
		retryInProgress = true
		return try {
			val callbackOwner = beginPhysicalDelivery() ?: return null
			val receipt = try {
				dispatch(event)
			} catch (_: Throwable) {
				null
			} finally {
				endPhysicalDelivery(callbackOwner)
			} ?: return null
			if (!consumeReturnedLifecycleReceipt(event, receipt)) return null
			receipt
		} finally {
			retryInProgress = false
			endPhysicalDelivery(owner)
		}
	}

	fun classifyReceipt(
		event: ReaderPresentationEvent.Lifecycle,
		receipt: ReaderPresentationEventReceipt?
	): ReaderPresentationLifecycleReceiptAdmission? {
		if (frozenDomain != null) return null
		if (event.event != nextPendingEvent()) return null
		if (!receiptAcknowledges(event, receipt)) return null
		return ReaderPresentationLifecycleReceiptAdmission(event, checkNotNull(receipt))
	}

	fun commitReceipt(admission: ReaderPresentationLifecycleReceiptAdmission) {
		if (frozenDomain != null) return
		acknowledge(admission.event.event, admission.receipt)
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		if (domain.readerSessionGeneration != expectedReaderSessionGeneration ||
			(frozenDomain != null && frozenDomain != domain)) return invalidPhysicalResource()
		if (frozenDomain == domain) {
			if (restoringRegisteredRoots) { restorationRefrozen = true; admissionEpoch++ }
			return ReaderPortCommandResult.Accepted
		}
		val result = physicalOwners.freezeForTransitionActivation(domain)
		if (result != ReaderPortCommandResult.Accepted) return result
		frozenDomain = domain
		admissionEpoch++
		restartSnapshot = captureRestartSnapshot()
		return ReaderPortCommandResult.Accepted
	}

	fun snapshotConnectedFrozenOwnership(): ReaderLegacyConnectedSourceInventory? {
		if (settlingReturns || restoringRegisteredRoots) return null
		return physicalOwners.connectedFrozenOwnership()?.takeIf { it.domain == frozenDomain }
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> =
		physicalOwners.snapshotFrozenOwnership()

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult = physicalOwners.drainFrozenOwnership(physicalIdentity, onConfirmed)

	fun finalizeActivatedHandoff(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult {
		if (legacyFinalized || frozenDomain != domain || physicalDepth != 0 || settlingReturns ||
			restoringRegisteredRoots || retryInProgress) return invalidPhysicalResource()
		val result = physicalOwners.finalizeActivatedHandoff(domain)
		if (result != ReaderPortCommandResult.Accepted) return result
		legacyFinalized = true
		restartSnapshot = null
		pendingHostEffectOwners.clear()
		restorationRootTokens.clear()
		return ReaderPortCommandResult.Accepted
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		if (legacyFinalized || frozenDomain != domain || physicalDepth != 0 || settlingReturns || restoringRegisteredRoots) {
			return invalidPhysicalResource()
		}
		// Pre-drain unfreeze is lawful. Once an actual root cancellation starts,
		// every old physical row must drain before any removed root is restarted.
		if (rootDrainDomain == domain && snapshotFrozenOwnership().any {
			it.physicalIdentity.sourceLocalToken !in restorationRootTokens
		}) return invalidPhysicalResource()
		restoringRegisteredRoots = true
		restorationRefrozen = false
		try {
			for (root in registeredRoots.toList()) {
				if (frozenDomain != domain || restorationRefrozen) return invalidPhysicalResource()
				if (!runCatching { root.restore(domain) }.getOrDefault(false)) return invalidPhysicalResource()
				if (frozenDomain != domain || restorationRefrozen || physicalDepth != 0) return invalidPhysicalResource()
			}
			val result = physicalOwners.restoreAfterTransitionActivation(domain)
			if (result != ReaderPortCommandResult.Accepted) return result
			restartSnapshot?.let(::restoreRestartSnapshot)
			restartSnapshot = null
			frozenDomain = null
			rootDrainDomain = null
			restorationRootTokens.clear()
			admissionEpoch++
			pendingHostEffectOwners.forEach(::registerPendingHostEffectPhysicalOwner)
			return ReaderPortCommandResult.Accepted
		} finally {
			restoringRegisteredRoots = false
		}
	}

	private fun invalidPhysicalResource(): ReaderPortCommandResult =
		ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)

	private fun captureRestartSnapshot() = RestartSnapshot(
		expectedReaderSessionGeneration = expectedReaderSessionGeneration,
		expectedPublicationIdentity = expectedPublicationIdentity,
		minimumEventSequence = minimumEventSequence,
		acknowledgedVersion = acknowledgedVersion,
		initialLifecycle = initialLifecycle,
		acknowledgedLifecycle = acknowledgedLifecycle,
		observedWindowVisible = observedWindowVisible,
		visibilityLossRequired = visibilityLossRequired,
		visibilityConfirmationRequired = visibilityConfirmationRequired,
		pendingMemoryPressure = pendingMemoryPressure.toSet(),
		rendererLossPending = rendererLossPending,
		publicationClosePending = publicationClosePending,
		publicationCloseAcknowledged = publicationCloseAcknowledged
	)

	private fun restoreRestartSnapshot(snapshot: RestartSnapshot) {
		expectedReaderSessionGeneration = snapshot.expectedReaderSessionGeneration
		expectedPublicationIdentity = snapshot.expectedPublicationIdentity
		minimumEventSequence = snapshot.minimumEventSequence
		acknowledgedVersion = snapshot.acknowledgedVersion
		initialLifecycle = snapshot.initialLifecycle
		acknowledgedLifecycle = snapshot.acknowledgedLifecycle
		observedWindowVisible = snapshot.observedWindowVisible
		visibilityLossRequired = snapshot.visibilityLossRequired
		visibilityConfirmationRequired = snapshot.visibilityConfirmationRequired
		pendingMemoryPressure.clear()
		pendingMemoryPressure += snapshot.pendingMemoryPressure
		rendererLossPending = snapshot.rendererLossPending
		publicationClosePending = snapshot.publicationClosePending
		publicationCloseAcknowledged = snapshot.publicationCloseAcknowledged
	}

	private fun nextPendingEvent(): ReaderPresentationLifecycleEvent? {
		if (publicationClosePending) return ReaderPresentationLifecycleEvent.PublicationClosed
		val visibility = nextVisibilityEvent()
		if (visibility == ReaderPresentationLifecycleEvent.VisibilityLost) return visibility
		if (rendererLossPending) return ReaderPresentationLifecycleEvent.RendererLost
		if (visibility != null) return visibility
		return pendingMemoryPressure.firstOrNull()
	}

	private fun nextVisibilityEvent(): ReaderPresentationLifecycleEvent? {
		if (
			visibilityLossRequired &&
			acknowledgedLifecycle != ReaderPresentationLifecycleState.Background
		) return ReaderPresentationLifecycleEvent.VisibilityLost
		if (!visibilityConfirmationRequired) return null
		return when (observedWindowVisible) {
			false -> ReaderPresentationLifecycleEvent.VisibilityLost.takeIf {
				acknowledgedLifecycle != ReaderPresentationLifecycleState.Background
			}
			true -> ReaderPresentationLifecycleEvent.VisibilityRestored.takeIf {
				acknowledgedLifecycle != ReaderPresentationLifecycleState.Foreground
			}
			null -> null
		}
	}

	private fun visibilityPendingEventCount(): Int {
		val next = nextVisibilityEvent() ?: return 0
		return if (
			next == ReaderPresentationLifecycleEvent.VisibilityLost &&
			observedWindowVisible == true
		) 2 else 1
	}

	private fun receiptAcknowledges(
		event: ReaderPresentationEvent.Lifecycle,
		receipt: ReaderPresentationEventReceipt?
	): Boolean {
		val expectedSession = expectedReaderSessionGeneration ?: return false
		val expectedPublication = expectedPublicationIdentity ?: return false
		if (
			receipt == null ||
			receipt.event != event ||
			receipt.version.readerSessionGeneration != expectedSession ||
			receipt.version.publicationIdentity != expectedPublication ||
			receipt.version.eventSequence < minimumEventSequence ||
			receipt.version.eventSequence <= (acknowledgedVersion?.eventSequence ?: -1L)
		) return false
		val postPublicationMatches = when (event.event) {
			ReaderPresentationLifecycleEvent.PublicationClosed -> receipt.postState.binding == null
			else -> receipt.postState.binding?.let { binding ->
				ReaderPresentationPublicationIdentity(
					foliateSessionId = binding.foliateSessionId,
					publicationGeneration = binding.publicationGeneration
				)
			} == expectedPublication
		}
		if (!postPublicationMatches) return false
		val expectedLifecycle = expectedPostLifecycle(event.event)
		if (receipt.postState.lifecycle != expectedLifecycle) return false
		return when (event.event) {
			ReaderPresentationLifecycleEvent.PublicationClosed ->
				receipt.disposition == ReaderPresentationEventDisposition.Accepted ||
					receipt.disposition == ReaderPresentationEventDisposition.Idempotent ||
					receipt.disposition == ReaderPresentationEventDisposition.Destroyed
			else -> receipt.disposition == ReaderPresentationEventDisposition.Accepted ||
				receipt.disposition == ReaderPresentationEventDisposition.Idempotent
		}
	}

	private fun expectedPostLifecycle(
		event: ReaderPresentationLifecycleEvent
	): ReaderPresentationLifecycleState = when (event) {
		ReaderPresentationLifecycleEvent.VisibilityLost -> ReaderPresentationLifecycleState.Background
		ReaderPresentationLifecycleEvent.VisibilityRestored -> ReaderPresentationLifecycleState.Foreground
		ReaderPresentationLifecycleEvent.PublicationClosed -> ReaderPresentationLifecycleState.Destroyed
		is ReaderPresentationLifecycleEvent.RunningMemoryPressure,
		is ReaderPresentationLifecycleEvent.BackgroundMemoryPressure,
		ReaderPresentationLifecycleEvent.HostDetached,
		ReaderPresentationLifecycleEvent.WebViewDetached,
		ReaderPresentationLifecycleEvent.RendererLost ->
			acknowledgedLifecycle ?: initialLifecycle
	}

	private fun acknowledge(
		event: ReaderPresentationLifecycleEvent,
		receipt: ReaderPresentationEventReceipt
	) {
		acknowledgedVersion = receipt.version
		minimumEventSequence = maxOf(minimumEventSequence, receipt.version.eventSequence)
		acknowledgedLifecycle = receipt.postState.lifecycle
		when (event) {
			ReaderPresentationLifecycleEvent.VisibilityLost -> {
				visibilityLossRequired = false
				visibilityConfirmationRequired = observedWindowVisible == true
			}
			ReaderPresentationLifecycleEvent.VisibilityRestored ->
				visibilityConfirmationRequired = observedWindowVisible != true
			is ReaderPresentationLifecycleEvent.RunningMemoryPressure,
			is ReaderPresentationLifecycleEvent.BackgroundMemoryPressure ->
				pendingMemoryPressure.remove(event)
			ReaderPresentationLifecycleEvent.HostDetached,
			ReaderPresentationLifecycleEvent.WebViewDetached -> Unit
			ReaderPresentationLifecycleEvent.RendererLost -> rendererLossPending = false
			ReaderPresentationLifecycleEvent.PublicationClosed -> {
				publicationClosePending = false
				publicationCloseAcknowledged = true
				visibilityLossRequired = false
				visibilityConfirmationRequired = false
				pendingMemoryPressure.clear()
				rendererLossPending = false
			}
		}
	}
}

internal const val ReaderPresentationHostEffectLimit = 16

internal data class ReaderPresentationHostEffectIdentity(
	val hostEpoch: Long,
	val version: ReaderPresentationReceiptVersion
)

internal data class ReaderPresentationHostEffect(
	val identity: ReaderPresentationHostEffectIdentity,
	val event: ReaderPresentationEvent?,
	val decision: ReaderPresentationDecision,
	val rendererLossCancellationIdentity: ReaderRendererLossCancellationIdentity?,
	val physicalAdmission: (() -> Boolean)? = null
) {
	val terminalPriority: Boolean
		get() = event is ReaderPresentationEvent.Lifecycle &&
			(
				event.event == ReaderPresentationLifecycleEvent.RendererLost ||
					event.event == ReaderPresentationLifecycleEvent.PublicationClosed
			)
}

internal enum class ReaderPresentationHostMutation {
	Model,
	ViewerDecision,
	ShellCoverLayer,
	ViewerCoverVisibility,
	NativeCoverVisibility
}

internal class ReaderPresentationHostEffectApplier(
	private val commitHostModel: (ReaderNativePresentationApplication) -> Unit,
	private val applyViewerDecision: (
		ReaderPresentationDecision,
		ReaderRendererLossCancellationIdentity?
	) -> Unit,
	private val applyShellCoverLayer: (ReaderNativePresentationApplication) -> Unit,
	private val applyViewerCoverVisibility: (ReaderNativePresentationApplication) -> Unit,
	private val applyNativeCoverVisibility: (ReaderNativePresentationApplication) -> Unit,
	private val afterMutation: (ReaderPresentationHostMutation) -> Unit = {}
) {
	private val completed = linkedSetOf<ReaderPresentationHostEffectIdentity>()
	private var currentHostEpoch: Long = -1L

	fun apply(effect: ReaderPresentationHostEffect) {
		when {
			effect.identity.hostEpoch < currentHostEpoch -> return
			effect.identity.hostEpoch > currentHostEpoch -> {
				currentHostEpoch = effect.identity.hostEpoch
				completed.clear()
			}
		}
		if (effect.identity in completed) return
		fun mutate(mutation: ReaderPresentationHostMutation, action: () -> Unit) {
			check(effect.physicalAdmission?.invoke() != false) { "Host effect admission is fenced" }
			action()
			check(effect.physicalAdmission?.invoke() != false) { "Host effect admission is fenced" }
			afterMutation(mutation)
			check(effect.physicalAdmission?.invoke() != false) { "Host effect admission is fenced" }
		}
		val application = readerNativePresentationApplication(effect.decision)
		mutate(ReaderPresentationHostMutation.Model) { commitHostModel(application) }
		mutate(ReaderPresentationHostMutation.ViewerDecision) {
			applyViewerDecision(application.decision, effect.rendererLossCancellationIdentity)
		}
		mutate(ReaderPresentationHostMutation.ShellCoverLayer) { applyShellCoverLayer(application) }
		mutate(ReaderPresentationHostMutation.ViewerCoverVisibility) { applyViewerCoverVisibility(application) }
		mutate(ReaderPresentationHostMutation.NativeCoverVisibility) { applyNativeCoverVisibility(application) }
		completed += effect.identity
		while (completed.size > ReaderPresentationHostEffectLimit) {
			completed.remove(completed.first())
		}
	}
}

internal class ReaderPresentationReceiptDispatcher(
	private val bindingReporter: ReaderPresentationBindingReporter,
	private val lifecycleDelivery: ReaderPresentationLifecycleDelivery,
	private val applyHostEffect: (ReaderPresentationHostEffect) -> Unit
) {
	constructor(
		bindingReporter: ReaderPresentationBindingReporter,
		lifecycleDelivery: ReaderPresentationLifecycleDelivery,
		applyDecision: (
			ReaderPresentationDecision,
			ReaderRendererLossCancellationIdentity?
		) -> Unit
	) : this(
		bindingReporter = bindingReporter,
		lifecycleDelivery = lifecycleDelivery,
		applyHostEffect = { effect ->
			applyDecision(effect.decision, effect.rendererLossCancellationIdentity)
		}
	)

	private var dispatchInProgress = false
	private val pendingHostEffects =
		linkedMapOf<ReaderPresentationHostEffectIdentity, ReaderPresentationHostEffect>()
	private val pendingHostEffectOwners = linkedMapOf<
		ReaderPresentationHostEffectIdentity, ReaderPresentationLifecycleDelivery.PendingHostEffectOwner
	>()
	private var hostEffectDrainInProgress = false
	private data class ParkedReceipt(
		val epoch: Long,
		val event: ReaderPresentationEvent,
		val receipt: ReaderPresentationEventReceipt,
		val rendererLossCancellationIdentity: ReaderRendererLossCancellationIdentity?
	)
	private var parkedReceipt: ParkedReceipt? = null

	val pendingHostEffectCount: Int
		get() = pendingHostEffects.size

	fun dispatch(
		event: ReaderPresentationEvent,
		onEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
	): ReaderPresentationEventReceipt? = dispatch(
		event = event,
		rendererLossCancellationIdentity = null,
		onEvent = onEvent
	)

	fun dispatch(
		event: ReaderPresentationEvent,
		rendererLossCancellationIdentity: ReaderRendererLossCancellationIdentity?,
		onEvent: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
	): ReaderPresentationEventReceipt? {
		if (dispatchInProgress || !lifecycleDelivery.physicalDeliveryAdmissionOpen) return null
		val owner = lifecycleDelivery.beginPhysicalDelivery() ?: return null
		val epoch = bindingReporter.captureEpoch()
		val admission = lifecycleDelivery.capturePhysicalAdmission()
		dispatchInProgress = true
		return try {
			resumeParkedReceipt()
			if (!lifecycleDelivery.admitsPhysicalDelivery(admission)) return null
			val recipient = lifecycleDelivery.beginPhysicalDelivery() ?: return null
			val receipt = try {
				onEvent(event)
			} catch (_: Throwable) {
				null
			} finally {
				lifecycleDelivery.endPhysicalDelivery(recipient)
			} ?: return null
			validateAndCommit(
				epoch = epoch,
				event = event,
				receipt = receipt,
				rendererLossCancellationIdentity = rendererLossCancellationIdentity
			)
		} finally {
			dispatchInProgress = false
			lifecycleDelivery.endPhysicalDelivery(owner)
		}
	}

	fun consumeReturned(
		epoch: Long,
		receipt: ReaderPresentationEventReceipt?
	): ReaderPresentationEventReceipt? {
		if (
			dispatchInProgress ||
				!lifecycleDelivery.physicalDeliveryAdmissionOpen ||
				receipt == null ||
				receipt.event is ReaderPresentationEvent.Lifecycle
		) {
			return null
		}
		val owner = lifecycleDelivery.beginPhysicalDelivery() ?: return null
		dispatchInProgress = true
		return try {
			resumeParkedReceipt()
			validateAndCommit(
				epoch = epoch,
				event = receipt.event,
				receipt = receipt,
				rendererLossCancellationIdentity = null
			)
		} finally {
			dispatchInProgress = false
			lifecycleDelivery.endPhysicalDelivery(owner)
		}
	}

	private fun validateAndCommit(
		epoch: Long,
		event: ReaderPresentationEvent,
		receipt: ReaderPresentationEventReceipt,
		rendererLossCancellationIdentity: ReaderRendererLossCancellationIdentity?
	): ReaderPresentationEventReceipt? {
		val reporterAdmission = bindingReporter.classifyReceipt(
			epoch = epoch,
			expectedEvent = event,
			receipt = receipt
		) ?: return null
		if (!lifecycleDelivery.physicalDeliveryAdmissionOpen) {
			if (event is ReaderPresentationEvent.Lifecycle &&
				!lifecycleDelivery.consumeReturnedLifecycleReceipt(event, receipt)) return null
			if (!lifecycleDelivery.legacyConsequencesOpen) return receipt
			// The accepted external recipient is consumed exactly once. Its model/effect
			// publication waits for restoration; restoration never invokes that recipient.
			parkedReceipt = ParkedReceipt(epoch, event, receipt, rendererLossCancellationIdentity)
			return receipt
		}
		val lifecycleAdmission = if (event is ReaderPresentationEvent.Lifecycle) {
			lifecycleDelivery.classifyReceipt(event, receipt) ?: return null
		} else {
			null
		}
		val cancellationIdentity = rendererLossCancellationIdentity.takeIf {
			event is ReaderPresentationEvent.Lifecycle &&
				event.event == ReaderPresentationLifecycleEvent.RendererLost
		}
		commitAuthoritativeModel(
			epoch = epoch,
			event = event,
			reporterAdmission = reporterAdmission,
			lifecycleAdmission = lifecycleAdmission,
			rendererLossCancellationIdentity = cancellationIdentity
		)
		drainPresentationHostEffects()
		return receipt
	}

	private fun resumeParkedReceipt() {
		if (!lifecycleDelivery.physicalDeliveryAdmissionOpen) return
		val parked = parkedReceipt ?: return
		parkedReceipt = null
		val admission = bindingReporter.classifyReceipt(parked.epoch, parked.event, parked.receipt)
			?: return
		commitAuthoritativeModel(parked.epoch, parked.event, admission, null,
			parked.rendererLossCancellationIdentity)
	}

	private fun commitAuthoritativeModel(
		epoch: Long,
		event: ReaderPresentationEvent,
		reporterAdmission: ReaderPresentationReceiptAdmission,
		lifecycleAdmission: ReaderPresentationLifecycleReceiptAdmission?,
		rendererLossCancellationIdentity: ReaderRendererLossCancellationIdentity?
	) {
		val receipt = reporterAdmission.receipt
		bindingReporter.commitReceipt(reporterAdmission)
		lifecycleAdmission?.let(lifecycleDelivery::commitReceipt)
		enqueueHostEffect(
			ReaderPresentationHostEffect(
				identity = ReaderPresentationHostEffectIdentity(epoch, receipt.version),
				event = event,
				decision = readerPresentationDecision(receipt.postState),
				rendererLossCancellationIdentity = rendererLossCancellationIdentity
			)
		)
	}

	private fun removeHostEffect(identity: ReaderPresentationHostEffectIdentity) {
		pendingHostEffects.remove(identity)
		pendingHostEffectOwners.remove(identity)?.let(lifecycleDelivery::completePendingHostEffect)
	}

	private fun removeHostEffectsWhere(predicate: (ReaderPresentationHostEffect) -> Boolean) {
		pendingHostEffects.values.filter(predicate).map { it.identity }.forEach(::removeHostEffect)
	}

	private fun enqueueHostEffect(effect: ReaderPresentationHostEffect) {
		removeHostEffectsWhere { pending ->
			pending.identity.hostEpoch == effect.identity.hostEpoch &&
				pending.identity.version.eventSequence < effect.identity.version.eventSequence &&
				(
					pending.rendererLossCancellationIdentity == null ||
						effect.rendererLossCancellationIdentity != null
				)
		}
		pendingHostEffects.entries.forEach { entry ->
			val pending = entry.value
			if (
				pending.identity.hostEpoch == effect.identity.hostEpoch &&
					pending.identity.version.eventSequence < effect.identity.version.eventSequence &&
					pending.rendererLossCancellationIdentity != null
			) {
				entry.setValue(pending.copy(decision = effect.decision))
			}
		}
		if (effect.identity !in pendingHostEffects) {
			val owner = lifecycleDelivery.registerPendingHostEffect() ?: return
			pendingHostEffects[effect.identity] = effect
			pendingHostEffectOwners[effect.identity] = owner
		}
		while (pendingHostEffects.size > ReaderPresentationHostEffectLimit) {
			val superseded = pendingHostEffects.entries.firstOrNull { (_, pending) ->
				pending.rendererLossCancellationIdentity == null &&
					pending.identity != effect.identity
			} ?: pendingHostEffects.entries.first()
			removeHostEffect(superseded.key)
		}
	}

	fun synchronizeComposeModel(
		epoch: Long,
		version: ReaderPresentationReceiptVersion,
		state: ReaderPresentationState,
		shellCoverVisible: Boolean,
		decision: ReaderPresentationDecision
	): Boolean {
		if (
			dispatchInProgress ||
			!lifecycleDelivery.physicalDeliveryAdmissionOpen ||
			epoch != bindingReporter.captureEpoch()
		) return false
		val owner = lifecycleDelivery.beginPhysicalDelivery() ?: return false
		dispatchInProgress = true
		try {
			resumeParkedReceipt()
			if (bindingReporter.matchesAuthoritativePresentationVersion(version)) {
				drainPresentationHostEffects()
				return true
			}
			if (decision != readerPresentationDecision(state)) {
				drainPresentationHostEffects()
				return false
			}
			if (
				!bindingReporter.consumeComposeModel(
					version = version,
					state = state,
					shellCoverVisible = shellCoverVisible
				)
			) {
				drainPresentationHostEffects()
				return false
			}
			enqueueHostEffect(
				ReaderPresentationHostEffect(
					identity = ReaderPresentationHostEffectIdentity(epoch, version),
					event = null,
					decision = decision,
					rendererLossCancellationIdentity = null
				)
			)
			drainPresentationHostEffects()
			return true
		} finally {
			dispatchInProgress = false
			lifecycleDelivery.endPhysicalDelivery(owner)
		}
	}

	fun drainPresentationHostEffects(): Boolean {
		if (!lifecycleDelivery.physicalDeliveryAdmissionOpen || hostEffectDrainInProgress) return false
		val owner = lifecycleDelivery.beginPhysicalDelivery() ?: return false
		val admission = lifecycleDelivery.capturePhysicalAdmission()
		hostEffectDrainInProgress = true
		return try {
			resumeParkedReceipt()
			val currentHostEpoch = bindingReporter.captureEpoch()
			removeHostEffectsWhere { it.identity.hostEpoch != currentHostEpoch }
			var complete = true
			val attempts = pendingHostEffects.values.sortedByDescending {
				it.identity.version.eventSequence
			}
			for (pending in attempts) {
				if (!lifecycleDelivery.admitsPhysicalDelivery(admission)) {
					complete = false
					break
				}
				if (pendingHostEffects[pending.identity] != pending) continue
				val effectOwner = lifecycleDelivery.beginPhysicalDelivery() ?: break
				val applied = try {
					applyHostEffect(pending.copy(physicalAdmission = {
						lifecycleDelivery.admitsPhysicalDelivery(admission)
					}))
					true
				} catch (_: Throwable) {
					false
				} finally {
					lifecycleDelivery.endPhysicalDelivery(effectOwner)
				}
				if (!applied) {
					complete = false
					continue
				}
				removeHostEffect(pending.identity)
				if (pending.terminalPriority) {
					removeHostEffectsWhere { queued ->
						queued.rendererLossCancellationIdentity == null &&
							queued.identity.hostEpoch == pending.identity.hostEpoch &&
							queued.identity.version.eventSequence <= pending.identity.version.eventSequence
					}
				}
			}
			complete && pendingHostEffects.isEmpty() && lifecycleDelivery.admitsPhysicalDelivery(admission)
		} finally {
			hostEffectDrainInProgress = false
			lifecycleDelivery.endPhysicalDelivery(owner)
		}
	}
}
