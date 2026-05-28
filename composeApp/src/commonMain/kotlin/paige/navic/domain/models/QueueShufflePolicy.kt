package paige.navic.domain.models

fun <Song> limitQueueShuffle(
	songs: List<Song>,
	limit: Int
): List<Song> =
	if (limit <= 0) songs else songs.take(limit)
