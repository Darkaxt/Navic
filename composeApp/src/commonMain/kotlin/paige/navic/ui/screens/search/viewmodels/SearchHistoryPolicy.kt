package paige.navic.ui.screens.search.viewmodels

fun shouldSaveSearchHistory(
	query: String,
	pauseSearchHistory: Boolean
): Boolean = !pauseSearchHistory && query.isNotBlank()

fun visibleSearchHistory(
	history: List<String>,
	pauseSearchHistory: Boolean
): List<String> = if (pauseSearchHistory) emptyList() else history
