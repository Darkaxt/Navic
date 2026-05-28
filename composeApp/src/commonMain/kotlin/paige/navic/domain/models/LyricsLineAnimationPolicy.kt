package paige.navic.domain.models

fun lyricsLineScale(
	animateSize: Boolean,
	isSynced: Boolean,
	isActive: Boolean,
	isSelectionMode: Boolean
): Float = when {
	!animateSize || !isSynced || isSelectionMode -> 1.0f
	isActive -> 1.05f
	else -> 0.98f
}
