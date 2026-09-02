package paige.navic.reader

enum class ReaderPagePreparationPhase {
	Idle,
	Preparing,
	Ready,
	Failed
}

fun readerPageTurnContentReadyKey(profile: ReaderPaginationProfileStatus): String? {
	if (profile.status != "cached" && profile.status != "ready") return null
	val fingerprint = profile.fingerprint?.takeIf { it.isNotBlank() } ?: return null
	val pageCount = profile.pageCount?.takeIf { it > 0 } ?: return null
	return "$fingerprint:$pageCount"
}

data class ReaderPagePreparationState(
	val phase: ReaderPagePreparationPhase = ReaderPagePreparationPhase.Idle,
	val preparationGeneration: Long = 0L,
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
	val progress: Float = 0f
)

fun ReaderPagePreparationState.toPresentationFacts(): ReaderPagePreparationFacts =
	ReaderPagePreparationFacts(
		phase = phase,
		generation = preparationGeneration,
		completedCount = completedCount,
		requiredCount = requiredCount,
		readiness = readiness,
		failure = if (phase == ReaderPagePreparationPhase.Failed) {
			ReaderPresentationFailureReason.PreparationFailed
		} else {
			null
		},
		retryable = retryable
	)

fun ReaderPagePreparationState.withReadiness(
	readiness: ReaderPageReadinessState
): ReaderPagePreparationState = copy(readiness = readiness)

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
	preparationGeneration: Long = 0L,
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
		preparationGeneration = preparationGeneration,
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
