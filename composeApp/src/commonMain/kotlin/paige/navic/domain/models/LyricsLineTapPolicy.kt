package paige.navic.domain.models

fun shouldSeekLyricsLineOnTap(
	isSelectionMode: Boolean,
	lyricsJumpOnTap: Boolean
): Boolean = !isSelectionMode && lyricsJumpOnTap
