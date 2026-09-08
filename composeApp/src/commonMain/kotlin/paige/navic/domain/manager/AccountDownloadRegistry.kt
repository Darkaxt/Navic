package paige.navic.domain.manager

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import paige.navic.data.database.dao.DownloadDao
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus

/** Account boundary for all runtime consumers; only migrations access the raw registry. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDownloadRegistry(
	private val dao: DownloadDao,
	val ownerId: StateFlow<String?>
) {
	fun owns(download: DownloadEntity): Boolean =
		ownerId.value?.takeIf { it.isNotEmpty() }?.let { it == download.ownerId } == true

	fun getAllDownloads(): Flow<List<DownloadEntity>> = ownerId.flatMapLatest { owner ->
		if (owner.isNullOrEmpty()) flowOf(emptyList()) else dao.getAllDownloads(owner)
			.map { rows -> if (ownerId.value == owner) rows.filter { it.ownerId == owner } else emptyList() }
			.onStart { emit(emptyList()) }
	}

	fun getDownloadsCount(status: DownloadStatus = DownloadStatus.DOWNLOADED): Flow<Int> =
		ownerId.flatMapLatest { owner ->
			if (owner.isNullOrEmpty()) flowOf(0) else dao.getDownloadsCount(owner, status)
				.map { count -> if (ownerId.value == owner) count else 0 }
				.onStart { emit(0) }
		}

	suspend fun getAllDownloadsList(): List<DownloadEntity> = scopedRead(emptyList()) { owner ->
		dao.getAllDownloadsList(owner).filter { it.ownerId == owner }
	}

	suspend fun getDownloadById(songId: String): DownloadEntity? = scopedRead(null) { owner ->
		dao.getDownloadById(owner, songId)?.takeIf { it.ownerId == owner }
	}

	suspend fun enqueueFreshIntent(songId: String, queuedAtEpochMs: Long): Long = scopedMutation(0L) { owner ->
		dao.enqueueFreshIntent(owner, songId, queuedAtEpochMs)
	}

	// Admission must not expose an outgoing claim; startup recovery requeues any hidden claim.
	suspend fun claimNextQueuedDownload(excludedSongIds: Set<String> = emptySet()): DownloadEntity? =
		scopedRead(null) { owner -> dao.claimNextQueuedDownload(owner, excludedSongIds) }

	suspend fun recoverInterruptedDownloads(): Int = scopedMutation(0) { dao.recoverInterruptedDownloads(it) }

	suspend fun cancelPendingIntent(songId: String): Int = scopedMutation(0) { dao.cancelPendingIntent(it, songId) }

	suspend fun retryFailedIntent(songId: String, generation: Long, queuedAtEpochMs: Long): Int = scopedMutation(0) {
		dao.retryFailedIntent(it, songId, generation, queuedAtEpochMs)
	}

	suspend fun updateProgressIfCurrent(
		songId: String, generation: Long, status: DownloadStatus, progress: Float
	): Int = scopedMutation(0) { dao.updateProgressIfCurrent(it, songId, generation, status, progress) }

	suspend fun completeIfCurrent(
		songId: String, generation: Long, status: DownloadStatus, progress: Float, filePath: String?
	): Int = scopedMutation(0) { dao.completeIfCurrent(it, songId, generation, status, progress, filePath) }

	suspend fun requeueIfCurrent(songId: String, generation: Long): Int = scopedMutation(0) {
		dao.requeueIfCurrent(it, songId, generation)
	}

	suspend fun deleteDownload(songId: String): Unit = scopedMutation(Unit) { dao.deleteDownload(it, songId) }

	suspend fun deleteDownloadIfCurrent(
		songId: String, generation: Long, expectedStatus: DownloadStatus, expectedFilePath: String?
	): Int = scopedMutation(0) {
		dao.deleteDownloadIfCurrent(it, songId, generation, expectedStatus, expectedFilePath)
	}

	suspend fun deleteFailedDownloadIfCurrent(songId: String, generation: Long): Int = scopedMutation(0) {
		dao.deleteFailedDownloadIfCurrent(it, songId, generation)
	}

	suspend fun clearAllDownloads(): Unit = scopedMutation(Unit) { dao.clearAllDownloads(it) }

	private suspend inline fun <T> scopedRead(unavailable: T, operation: (String) -> T): T {
		val owner = ownerId.value?.takeIf { it.isNotEmpty() } ?: return unavailable
		val result = operation(owner)
		return if (ownerId.value == owner) result else unavailable
	}

	private suspend inline fun <T> scopedMutation(unavailable: T, operation: (String) -> T): T {
		val owner = ownerId.value?.takeIf { it.isNotEmpty() } ?: return unavailable
		// A committed write remains committed after logout; publication needs its exact acknowledgment.
		return operation(owner)
	}
}
