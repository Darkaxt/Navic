package paige.navic.domain.models

enum class QueueDuplicateAction {
	AddToQueue,
	PlayNext
}

fun requiresQueueDuplicateConfirmation(
	queueSongIds: Collection<String>,
	songId: String
): Boolean = songId in queueSongIds

fun duplicateQueueActionFor(
	queueSongIds: Collection<String>,
	songId: String,
	action: QueueDuplicateAction
): QueueDuplicateAction? =
	action.takeIf {
		requiresQueueDuplicateConfirmation(queueSongIds, songId)
	}
