package paige.navic.ui.screens.artist

private const val TopSongRowHeightDp = 84
private const val MaxTopSongRows = 3

fun artistTopSongsGridRows(songCount: Int): Int =
	songCount.coerceIn(0, MaxTopSongRows)

fun artistTopSongsGridHeightDp(songCount: Int): Int =
	artistTopSongsGridRows(songCount) * TopSongRowHeightDp
