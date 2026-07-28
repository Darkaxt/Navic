package paige.navic.reader

enum class ReaderPagePreparationPhase {
	Idle,
	Preparing,
	Ready,
	Failed
}

enum class ReaderPagePreparationPresentation {
	Hidden,
	Cover,
	Compact
}

enum class ReaderPagePreparationGestureDisposition {
	Allow,
	ConsumeWhilePreparing
}

fun readerPageTurnContentReadyKey(profile: ReaderPaginationProfileStatus): String? {
	if (profile.status != "cached" && profile.status != "ready") return null
	val fingerprint = profile.fingerprint?.takeIf { it.isNotBlank() } ?: return null
	val pageCount = profile.pageCount?.takeIf { it > 0 } ?: return null
	return "$fingerprint:$pageCount"
}

data class ReaderPagePreparationState(
	val phase: ReaderPagePreparationPhase = ReaderPagePreparationPhase.Idle,
	val requiredCount: Int = 0,
	val completedCount: Int = 0,
	val interactiveRequiredCount: Int = 0,
	val interactiveCompletedCount: Int = 0,
	val activePageLabel: String? = null,
	val error: String? = null,
	val retryable: Boolean = false,
	val readiness: ReaderPageReadinessState = ReaderPageReadinessState(
		interaction = ReaderPageInteractionState.Ready
	),
	val operationPolicy: ReaderPageOperationPolicy = readerPageOperationPolicy(readiness),
	val progress: Float = 0f,
	val presentation: ReaderPagePreparationPresentation = ReaderPagePreparationPresentation.Hidden,
	val gestureDisposition: ReaderPagePreparationGestureDisposition = ReaderPagePreparationGestureDisposition.Allow
) {
	val interactiveReady: Boolean
		get() = operationPolicy.newPointer is ReaderPageNewPointerDecision.Accept
}

private fun readerPagePreparationPresentation(
	readiness: ReaderPageReadinessState
): ReaderPagePreparationPresentation = when (readiness.interaction) {
	ReaderPageInteractionState.BlockingInitialPreparation,
	ReaderPageInteractionState.BlockingProfileRegeneration -> ReaderPagePreparationPresentation.Cover
	ReaderPageInteractionState.Failed -> if (
		readiness.textureDeck == ReaderTextureDeckState.Ready &&
			readiness.decodedWorkingSet == ReaderDecodedWorkingSetState.Ready
	) {
		ReaderPagePreparationPresentation.Compact
	} else {
		ReaderPagePreparationPresentation.Cover
	}
	ReaderPageInteractionState.Ready,
	ReaderPageInteractionState.Settling,
	ReaderPageInteractionState.BackgroundPrefetch,
	ReaderPageInteractionState.RefillingWorkingSet -> ReaderPagePreparationPresentation.Hidden
}

fun ReaderPagePreparationState.withReadiness(
	readiness: ReaderPageReadinessState
): ReaderPagePreparationState {
	val operationPolicy = readerPageOperationPolicy(readiness)
	return copy(
		readiness = readiness,
		operationPolicy = operationPolicy,
		presentation = readerPagePreparationPresentation(readiness),
		gestureDisposition = if (operationPolicy.newPointer is ReaderPageNewPointerDecision.Accept) {
			ReaderPagePreparationGestureDisposition.Allow
		} else {
			ReaderPagePreparationGestureDisposition.ConsumeWhilePreparing
		}
	)
}

fun ReaderPagePreparationState.withRendererReadiness(
	renderer: ReaderPageRendererReadinessState
): ReaderPagePreparationState = withReadiness(
	readiness.copy(
		textureDeck = renderer.textureDeck,
		pendingTextureDeck = renderer.pendingTextureDeck,
		interaction = when {
			phase == ReaderPagePreparationPhase.Failed ||
				renderer.interaction == ReaderPageInteractionState.Failed ->
				ReaderPageInteractionState.Failed
			readiness.interaction == ReaderPageInteractionState.BlockingInitialPreparation ||
				readiness.interaction == ReaderPageInteractionState.BlockingProfileRegeneration ->
				readiness.interaction
			else -> renderer.interaction
		}
	)
)

fun readerPagePreparationState(
	phase: ReaderPagePreparationPhase,
	requiredCount: Int,
	completedCount: Int,
	interactiveRequiredCount: Int,
	interactiveCompletedCount: Int,
	readiness: ReaderPageReadinessState,
	activePageNumber: Int? = null,
	error: String? = null,
	retryable: Boolean = false
): ReaderPagePreparationState {
	val normalizedRequired = requiredCount.coerceAtLeast(0)
	val normalizedCompleted = completedCount.coerceIn(0, normalizedRequired)
	val normalizedInteractiveRequired = interactiveRequiredCount.coerceAtLeast(0)
	val normalizedInteractiveCompleted = interactiveCompletedCount.coerceIn(0, normalizedInteractiveRequired)
	return ReaderPagePreparationState(
		phase = phase,
		requiredCount = normalizedRequired,
		completedCount = normalizedCompleted,
		interactiveRequiredCount = normalizedInteractiveRequired,
		interactiveCompletedCount = normalizedInteractiveCompleted,
		activePageLabel = activePageNumber?.takeIf { it > 0 }?.let { page -> "Page $page" },
		error = error,
		retryable = retryable,
		readiness = readiness,
		progress = if (normalizedRequired == 0) 0f else {
			normalizedCompleted.toFloat() / normalizedRequired.toFloat()
		},
	).withReadiness(readiness)
}
