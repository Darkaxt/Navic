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
	val interactiveReady: Boolean = false,
	val progress: Float = 0f,
	val presentation: ReaderPagePreparationPresentation = ReaderPagePreparationPresentation.Hidden,
	val gestureDisposition: ReaderPagePreparationGestureDisposition = ReaderPagePreparationGestureDisposition.Allow
)

fun readerPagePreparationState(
	phase: ReaderPagePreparationPhase,
	requiredCount: Int,
	completedCount: Int,
	interactiveRequiredCount: Int,
	interactiveCompletedCount: Int,
	hasPreparedBefore: Boolean,
	activePageNumber: Int? = null,
	error: String? = null,
	retryable: Boolean = false
): ReaderPagePreparationState {
	val normalizedRequired = requiredCount.coerceAtLeast(0)
	val normalizedCompleted = completedCount.coerceIn(0, normalizedRequired)
	val normalizedInteractiveRequired = interactiveRequiredCount.coerceAtLeast(0)
	val normalizedInteractiveCompleted = interactiveCompletedCount.coerceIn(0, normalizedInteractiveRequired)
	val interactiveReady = normalizedInteractiveRequired > 0 &&
		normalizedInteractiveCompleted >= normalizedInteractiveRequired
	val presentation = when (phase) {
		ReaderPagePreparationPhase.Idle,
		ReaderPagePreparationPhase.Ready -> ReaderPagePreparationPresentation.Hidden
		ReaderPagePreparationPhase.Preparing -> if (!hasPreparedBefore && !interactiveReady) {
			ReaderPagePreparationPresentation.Cover
		} else {
			ReaderPagePreparationPresentation.Hidden
		}
		ReaderPagePreparationPhase.Failed -> if (hasPreparedBefore) {
			ReaderPagePreparationPresentation.Compact
		} else {
			ReaderPagePreparationPresentation.Cover
		}
	}
	return ReaderPagePreparationState(
		phase = phase,
		requiredCount = normalizedRequired,
		completedCount = normalizedCompleted,
		interactiveRequiredCount = normalizedInteractiveRequired,
		interactiveCompletedCount = normalizedInteractiveCompleted,
		activePageLabel = activePageNumber?.takeIf { it > 0 }?.let { page -> "Page $page" },
		error = error,
		retryable = retryable,
		interactiveReady = interactiveReady,
		progress = if (normalizedRequired == 0) 0f else {
			normalizedCompleted.toFloat() / normalizedRequired.toFloat()
		},
		presentation = presentation,
		gestureDisposition = if (phase == ReaderPagePreparationPhase.Preparing && !interactiveReady) {
			ReaderPagePreparationGestureDisposition.ConsumeWhilePreparing
		} else {
			ReaderPagePreparationGestureDisposition.Allow
		}
	)
}
