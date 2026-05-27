package paige.navic.domain.models

const val QueueAutoFillRemainingTrigger = 10

fun shouldAutoFillQueue(
	autoFillQueue: Boolean,
	isPlaying: Boolean,
	isRadioQueue: Boolean,
	queueSize: Int,
	currentIndex: Int,
	remainingTrigger: Int,
	targetSize: Int
): Boolean {
	if (!autoFillQueue || !isPlaying || isRadioQueue) return false
	if (queueSize <= 0 || currentIndex !in 0..<queueSize) return false
	if (queueSize >= targetSize.coerceAtLeast(0)) return false

	val remainingAfterCurrent = queueSize - currentIndex - 1
	return remainingAfterCurrent <= remainingTrigger.coerceAtLeast(0)
}

fun queueAutoFillAppendCount(
	queueSize: Int,
	targetSize: Int
): Int = (targetSize.coerceAtLeast(0) - queueSize.coerceAtLeast(0)).coerceAtLeast(0)

fun queueAutoFillCandidateIds(
	candidateIds: List<String>,
	queuedIds: Set<String>,
	limit: Int
): List<String> {
	val seen = queuedIds.toMutableSet()
	return candidateIds
		.asSequence()
		.filterNot { it.startsWith("radio_") }
		.filter { seen.add(it) }
		.take(limit.coerceAtLeast(0))
		.toList()
}
