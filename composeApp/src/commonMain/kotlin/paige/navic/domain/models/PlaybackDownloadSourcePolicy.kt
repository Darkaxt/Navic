package paige.navic.domain.models

fun shouldReplaceQueuedMediaItemForDownloadAvailability(
	isCurrentItem: Boolean,
	hasDownloadedFile: Boolean,
	isCurrentlyLocal: Boolean,
	isRecoveringFromSourceError: Boolean = false
): Boolean =
	hasDownloadedFile != isCurrentlyLocal &&
		(!isCurrentItem || isRecoveringFromSourceError)
