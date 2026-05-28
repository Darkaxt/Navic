package paige.navic.domain.models

fun shouldReplaceQueuedMediaItemForDownloadAvailability(
	isCurrentItem: Boolean,
	hasDownloadedFile: Boolean,
	isCurrentlyLocal: Boolean
): Boolean =
	!isCurrentItem && hasDownloadedFile != isCurrentlyLocal
