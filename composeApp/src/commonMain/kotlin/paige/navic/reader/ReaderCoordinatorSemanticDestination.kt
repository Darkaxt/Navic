package paige.navic.reader

/** Original owning adapter/controller/progress/WordSync ingress followed by one semantic
 * presentation receipt. It does not replay legacy physical presentation consequences. */
internal fun ReaderCoordinator.onInstalledPageTurnRelocation(
	event: ReaderBridgeEvent.LocationChanged,
	predecessor: ReaderPresentationBinding,
	origin: ReaderPresentationEventOrigin.SemanticCommand
): ReaderCoordinatorStep {
	val state = controller.state
	val destination = event.destinationCommitIdentity ?: return ReaderCoordinatorStep(this)
	val expected = origin.transitionId.expectedBinding as? ReaderExpectedPresentationBinding.SemanticSuccessor
		?: return ReaderCoordinatorStep(this)
	if (expected is ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial ||
		expected is ReaderExpectedPresentationBinding.NoCommittedPresentation ||
		expected.predecessor != predecessor || state.presentation.binding != predecessor ||
		state.destinationCommitIdentity != predecessor.destinationCommitIdentity ||
		state.presentation.lifecycle != ReaderPresentationLifecycleState.Foreground || state.presentation.failure != null ||
		origin.transitionId.operation != ReaderTransitionOperation.CurlClaimAndSettlement ||
		origin.transitionId.readerSessionGeneration != state.readerSessionGeneration ||
		event.foliateSessionId != predecessor.foliateSessionId || destination.foliateSessionId != event.foliateSessionId ||
		state.foliateSessionId?.let { it != event.foliateSessionId } == true ||
		predecessor.destinationCommitIdentity?.let { destination.commitSequence <= it.commitSequence } != false ||
		event.pageTurnSettleToken.isNullOrBlank() || event.pageTurnSettleSessionId != event.foliateSessionId ||
		(event.locator.pageIndex ?: -1) < 0 || (event.pageTurnSettleRasterGeneration ?: -1L) < 0L ||
		(event.pageTurnSettleTextureGeneration ?: -1L) < 0L) return ReaderCoordinatorStep(this)
	val data = onEngineHostEvent(ReaderEngineHostEvent.FoliateBridge(event))
	val owningState = data.coordinator.controller.state
	val acknowledgement = owningState.pageTurnSettlementAck ?: return ReaderCoordinatorStep(this)
	if (owningState.destinationCommitIdentity != destination ||
		acknowledgement.token != event.pageTurnSettleToken || acknowledgement.pageIndex != event.locator.pageIndex ||
		acknowledgement.foliateSessionId != event.pageTurnSettleSessionId ||
		acknowledgement.rasterGeneration != event.pageTurnSettleRasterGeneration ||
		acknowledgement.textureGeneration != event.pageTurnSettleTextureGeneration) return ReaderCoordinatorStep(this)
	val binding = predecessor.copy(destinationCommitIdentity = destination,
		preparationGeneration = null, rasterGeneration = null, textureGeneration = null)
	val presentation = data.coordinator.controller.onPresentationEvent(
		ReaderPresentationEvent.FoliateRelocated(binding, acknowledgement), origin)
	return data.copy(coordinator = data.coordinator.copy(controller = presentation.controller),
		presentationEffects = emptyList(), presentationReceipt = presentation.presentationReceipt)
}


/** Visual projection of an original accepted publication, not another physical or semantic commit.
 * The installed coordinator authenticates the callback before predecessor release. */
internal fun ReaderCoordinator.onInstalledPhysicalPublication(
	command: ReaderTransitionCommand,
	owner: ReaderPresentationFrameOwner,
	binding: ReaderPresentationBinding,
	lease: ReaderTransitionInputLease
): ReaderCoordinator {
	val state = controller.state
	val transitionId = command.transitionId ?: return this
	if (transitionId.readerSessionGeneration != state.readerSessionGeneration ||
		state.presentation.lifecycle == ReaderPresentationLifecycleState.Destroyed ||
		state.foliateSessionId?.let { it != binding.foliateSessionId } == true ||
		state.presentation.binding?.let { it.publicationIdentity != binding.publicationIdentity } == true
	) return this
	val matchesCommand = when (command) {
		is ReaderTransitionCommand.CommitOwnerAndInputLease ->
			command.owner == owner && command.binding == binding
		is ReaderTransitionCommand.PublishRetainedOwnerAndInputLease ->
			command.retainedOwner == owner && command.retainedBinding == binding
		else -> false
	}
	if (!matchesCommand) return this
	val authority = when (owner) {
		ReaderPresentationFrameOwner.Neutral -> ReaderPresentationAuthority.Unavailable
		is ReaderPresentationFrameOwner.ShellCover -> ReaderPresentationAuthority.ShellCover(owner.proof)
		is ReaderPresentationFrameOwner.NativePage -> ReaderPresentationAuthority.SettledNativePage(owner)
		is ReaderPresentationFrameOwner.Curl -> ReaderPresentationAuthority.CurlGesture(owner)
		is ReaderPresentationFrameOwner.LiveEngine -> ReaderPresentationAuthority.LiveEngineExposed(owner)
	}
	return copy(controller = controller.copy(state = state.copy(presentation = state.presentation.copy(
		authority = authority,
		binding = binding,
		installedPhysicalInputLease = lease
	))))
}

// Semantic observations update only the common presentation model, never WordSync or physical hosts.
internal fun ReaderCoordinator.reduceSemanticDestination(
	event: ReaderPresentationEvent.FoliateRelocated,
	origin: ReaderPresentationEventOrigin.SemanticCommand
): ReaderCoordinatorStep {
	if (origin.transitionId.readerSessionGeneration != controller.state.readerSessionGeneration) return ReaderCoordinatorStep(this)
	val step = controller.onPresentationEvent(event, origin)
	return ReaderCoordinatorStep(copy(controller = step.controller), presentationReceipt = step.presentationReceipt)
}

internal fun ReaderCoordinator.reduceSemanticDestination(
	event: ReaderPresentationEvent.CurrentDestinationConfirmed,
	origin: ReaderPresentationEventOrigin.SemanticCommand
): ReaderCoordinatorStep {
	if (origin.transitionId.readerSessionGeneration != controller.state.readerSessionGeneration) return ReaderCoordinatorStep(this)
	val step = controller.onPresentationEvent(event, origin)
	return ReaderCoordinatorStep(copy(controller = step.controller), presentationReceipt = step.presentationReceipt)
}
