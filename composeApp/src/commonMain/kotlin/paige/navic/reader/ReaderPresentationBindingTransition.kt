package paige.navic.reader

internal fun ReaderPresentationBinding.hasCompleteRendererIdentity(): Boolean =
	rasterGeneration != null && textureGeneration != null

internal fun ReaderPresentationBinding.hasAnyRendererIdentity(): Boolean =
	rasterGeneration != null || textureGeneration != null

internal fun ReaderPresentationBinding.isExactRendererCompletionOf(
	predecessor: ReaderPresentationBinding
): Boolean = !predecessor.hasAnyRendererIdentity() &&
	hasCompleteRendererIdentity() &&
	foliateSessionId == predecessor.foliateSessionId &&
	publicationGeneration == predecessor.publicationGeneration &&
	viewportGeneration == predecessor.viewportGeneration &&
	profileGeneration == predecessor.profileGeneration &&
	destinationCommitIdentity == predecessor.destinationCommitIdentity &&
	preparationGeneration != null &&
	preparationGeneration == predecessor.preparationGeneration

internal fun ReaderPresentationBinding.sharesRendererDeckWith(
	other: ReaderPresentationBinding
): Boolean = rendererDeckIdentityOrNull()?.let { identity ->
	identity == other.rendererDeckIdentityOrNull()
} == true

internal fun ReaderPresentationBinding.isCausalDestinationSuccessorOf(
	predecessor: ReaderPresentationBinding
): Boolean {
	val predecessorDestination = predecessor.destinationCommitIdentity ?: return false
	val destination = destinationCommitIdentity ?: return false
	return foliateSessionId == predecessor.foliateSessionId &&
		publicationGeneration == predecessor.publicationGeneration &&
		viewportGeneration == predecessor.viewportGeneration &&
		profileGeneration == predecessor.profileGeneration &&
		(
			preparationGeneration == predecessor.preparationGeneration ||
				predecessor.hasCompleteRendererIdentity() &&
				hasCompleteRendererIdentity() &&
				preparationGeneration != null
		) &&
		destination.commitSequence > predecessorDestination.commitSequence &&
		(!hasAnyRendererIdentity() || hasCompleteRendererIdentity())
}

private fun Long?.isSameOrNewerThan(predecessor: Long?): Boolean = when {
	predecessor == null -> true
	this == null -> false
	else -> this >= predecessor
}

internal fun ReaderPresentationBinding.isSafeBindingReplacementOf(
	predecessor: ReaderPresentationBinding
): Boolean {
	if (
		this == predecessor ||
		foliateSessionId != predecessor.foliateSessionId ||
		publicationGeneration != predecessor.publicationGeneration
	) return false
	if (!predecessor.hasAnyRendererIdentity() && !hasAnyRendererIdentity()) {
		return destinationCommitIdentity == predecessor.destinationCommitIdentity &&
			viewportGeneration >= predecessor.viewportGeneration &&
			profileGeneration >= predecessor.profileGeneration &&
			preparationGeneration.isSameOrNewerThan(predecessor.preparationGeneration)
	}
	if (
		!predecessor.hasCompleteRendererIdentity() ||
		!hasCompleteRendererIdentity() ||
		preparationGeneration == null ||
		viewportGeneration < predecessor.viewportGeneration ||
		profileGeneration < predecessor.profileGeneration
	) return false
	val predecessorDestination = predecessor.destinationCommitIdentity
	val destination = destinationCommitIdentity
	if (destination == predecessorDestination) return true
	return (viewportGeneration != predecessor.viewportGeneration ||
		profileGeneration != predecessor.profileGeneration) &&
		predecessorDestination != null &&
		destination != null &&
		destination.commitSequence > predecessorDestination.commitSequence
}

internal fun readerPresentationBindingEventCandidate(
	previousBinding: ReaderPresentationBinding,
	currentBinding: ReaderPresentationBinding,
	relocationPending: Boolean,
	relocationAcknowledgement: ReaderPageTurnSettlementAck? = null
): ReaderPresentationEvent? = when {
	previousBinding == currentBinding -> null
	previousBinding.foliateSessionId != currentBinding.foliateSessionId -> null
	previousBinding.publicationGeneration != currentBinding.publicationGeneration -> null
	currentBinding.isExactRendererCompletionOf(previousBinding) ->
		ReaderPresentationEvent.BindingCompleted(previousBinding, currentBinding)
	currentBinding.isCausalDestinationSuccessorOf(previousBinding) ->
		ReaderPresentationEvent.FoliateRelocated(
			binding = currentBinding,
			acknowledgement = relocationAcknowledgement
		).takeIf { relocationPending }
	currentBinding.isSafeBindingReplacementOf(previousBinding) ->
		ReaderPresentationEvent.BindingReplaced(previousBinding, currentBinding)
	else -> null
}
