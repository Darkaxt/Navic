package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus

internal fun downloadedAudioPath(
	download: DownloadEntity?,
	ownerId: String?,
	isUsable: (String) -> Boolean
): String? {
	if (ownerId == null || download?.ownerId != ownerId || download.status != DownloadStatus.DOWNLOADED) return null
	return download.filePath?.takeIf(isUsable)
}
