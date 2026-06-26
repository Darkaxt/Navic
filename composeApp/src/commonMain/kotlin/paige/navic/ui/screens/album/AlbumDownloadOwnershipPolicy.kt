package paige.navic.ui.screens.album

import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.DomainAlbum

fun albumDownloadOwnershipStatus(
	songIds: List<String>,
	downloads: List<DownloadEntity>
): AurralOwnershipStatus? {
	val distinctSongIds = songIds.distinct().filter { it.isNotBlank() }
	if (distinctSongIds.isEmpty()) return null
	val downloadBySongId = downloads
		.filter { it.songId in distinctSongIds }
		.associateBy { it.songId }
	if (downloadBySongId.isEmpty()) return null

	return when {
		downloadBySongId.values.any { it.status == DownloadStatus.FAILED } ->
			AurralOwnershipStatus.Failed
		downloadBySongId.values.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED } ->
			AurralOwnershipStatus.Processing
		distinctSongIds.all { songId -> downloadBySongId[songId]?.status == DownloadStatus.DOWNLOADED } ->
			AurralOwnershipStatus.Owned
		downloadBySongId.values.any { it.status == DownloadStatus.DOWNLOADED } ->
			AurralOwnershipStatus.Partial
		else -> null
	}
}

fun albumDownloadOwnershipStatuses(
	albums: List<DomainAlbum>,
	downloads: List<DownloadEntity>
): Map<String, AurralOwnershipStatus> =
	albums.mapNotNull { album ->
		val status = albumDownloadOwnershipStatus(
			songIds = album.songs.map { song -> song.id },
			downloads = downloads
		) ?: return@mapNotNull null
		album.id to status
	}.toMap()
