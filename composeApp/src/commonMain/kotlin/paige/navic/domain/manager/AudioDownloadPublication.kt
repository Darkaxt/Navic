package paige.navic.domain.manager

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal suspend fun publishAudioDownload(
	finalPath: String,
	write: suspend (String) -> Unit,
	isUsable: (String) -> Boolean,
	move: (String, String) -> Boolean,
	complete: suspend (String) -> Boolean,
	delete: (String) -> Unit
): Boolean {
	val temporaryPath = "$finalPath.part"
	var committed = false
	try {
		write(temporaryPath)
		check(isUsable(temporaryPath)) { "Audio download produced an empty or missing file" }
		currentCoroutineContext().ensureActive()
		withContext(NonCancellable) {
			check(move(temporaryPath, finalPath)) { "Unable to publish downloaded audio" }
			committed = complete(finalPath)
		}
		return committed
	} finally {
		withContext(NonCancellable) {
			delete(temporaryPath)
			if (!committed) delete(finalPath)
		}
	}
}

internal suspend fun deletePublishedAudio(
	deleteRow: suspend () -> Boolean,
	deleteFiles: () -> Unit
): Boolean = withContext(NonCancellable) {
	if (!deleteRow()) return@withContext false
	deleteFiles()
	true
}
