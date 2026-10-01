package paige.navic.domain.models

fun shouldReplaceQueuedMediaItemForDownloadAvailability(
	isCurrentItem: Boolean,
	hasDownloadedFile: Boolean,
	isCurrentlyLocal: Boolean,
	isRecoveringFromSourceError: Boolean = false,
	hasSourceUriChanged: Boolean = false
): Boolean =
	(hasDownloadedFile != isCurrentlyLocal || hasSourceUriChanged) &&
		(!isCurrentItem || isRecoveringFromSourceError)
