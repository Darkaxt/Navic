package paige.navic.ui.screens.search.viewmodels

private const val SearchHistoryLimit = 10

private fun normalizedSearchQuery(query: String): String =
	query
		.replace('\r', ' ')
		.replace('\n', ' ')
		.trim()

private fun sanitizedSearchHistory(history: List<String>): List<String> =
	history
		.map(::normalizedSearchQuery)
		.filter { it.isNotEmpty() }
		.distinct()
		.take(SearchHistoryLimit)

fun shouldSaveSearchHistory(
	query: String,
	pauseSearchHistory: Boolean
): Boolean = !pauseSearchHistory && normalizedSearchQuery(query).isNotEmpty()

fun visibleSearchHistory(
	history: List<String>,
	pauseSearchHistory: Boolean
): List<String> = if (pauseSearchHistory) emptyList() else history

fun updatedSearchHistoryAfterSubmit(
	query: String,
	history: List<String>,
	pauseSearchHistory: Boolean
): List<String> {
	if (!shouldSaveSearchHistory(query, pauseSearchHistory)) return history

	val normalizedQuery = normalizedSearchQuery(query)
	return sanitizedSearchHistory(
		listOf(normalizedQuery) + history.filterNot { normalizedSearchQuery(it) == normalizedQuery }
	)
}

fun updatedSearchHistoryAfterRemoval(
	query: String,
	history: List<String>
): List<String> {
	val normalizedQuery = normalizedSearchQuery(query)
	return sanitizedSearchHistory(history.filterNot { normalizedSearchQuery(it) == normalizedQuery })
}

fun encodeSearchHistory(history: List<String>): String =
	sanitizedSearchHistory(history).joinToString(separator = "\n")

fun decodeSearchHistory(raw: String): List<String> =
	sanitizedSearchHistory(raw.lines())
