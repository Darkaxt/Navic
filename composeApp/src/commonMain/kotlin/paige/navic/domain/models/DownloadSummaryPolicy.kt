package paige.navic.domain.models

import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus

fun pendingDownloadCount(downloads: List<DownloadEntity>): Int =
	downloads.count { download ->
		download.status == DownloadStatus.QUEUED ||
			download.status == DownloadStatus.DOWNLOADING
	}
