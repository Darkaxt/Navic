package paige.navic.ui.screens.artist

import paige.navic.ui.navigation.Screen

/** Artist header genre labels open the same genre detail route as the genre list cards. */
fun artistGenreDestination(genre: String): Screen? =
	genre.trim()
		.takeIf { it.isNotEmpty() }
		?.let { Screen.GenreDetail(it) }
