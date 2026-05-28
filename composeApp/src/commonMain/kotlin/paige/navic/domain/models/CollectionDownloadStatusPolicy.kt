package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus

fun collectionDownloadStatus(
	songIds: List<String>,
	downloads: List<DownloadEntity>
): DownloadStatus {
	val collectionDownloads = downloads.filter { it.songId in songIds }
	return when {
		collectionDownloads.isEmpty() -> DownloadStatus.NOT_DOWNLOADED
		collectionDownloads.any { it.status == DownloadStatus.DOWNLOADING } -> DownloadStatus.DOWNLOADING
		collectionDownloads.any { it.status == DownloadStatus.QUEUED } -> DownloadStatus.QUEUED
		collectionDownloads.any { it.status == DownloadStatus.FAILED } -> DownloadStatus.FAILED
		collectionDownloads.size == songIds.size &&
			collectionDownloads.all { it.status == DownloadStatus.DOWNLOADED } -> DownloadStatus.DOWNLOADED

		else -> DownloadStatus.NOT_DOWNLOADED
	}
}

fun collectionSongIdsToQueue(
	songIds: List<String>,
	downloads: List<DownloadEntity>
): List<String> {
	val downloadsBySongId = downloads.associateBy { it.songId }
	return songIds.filter { songId ->
		when (downloadsBySongId[songId]?.status) {
			DownloadStatus.DOWNLOADED,
			DownloadStatus.DOWNLOADING,
			DownloadStatus.QUEUED -> false

			DownloadStatus.NOT_DOWNLOADED,
			DownloadStatus.FAILED,
			null -> true
		}
	}
}
