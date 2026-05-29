package paige.navic.ui.screens.settings

data class SettingsSearchEntryText(
	val id: String,
	val path: String,
	val title: String,
	val subtitle: String? = null,
	val keywords: List<String> = emptyList()
)

data class SettingsSearchEntryGroup(
	val path: String,
	val entries: List<SettingsSearchEntryText>
)

data class SettingsSearchResultItem(
	val path: String,
	val entry: SettingsSearchEntryText
)

fun filteredSettingsSearchEntries(
	entries: List<SettingsSearchEntryText>,
	query: String
): List<SettingsSearchEntryText> {
	val terms = query.searchTerms()
	if (terms.isEmpty()) return emptyList()

	return entries.filter { entry ->
		val haystack = entry.searchHaystack()
		terms.all { term -> haystack.contains(term) }
	}
}

fun filteredSettingsSearchEntryGroups(
	entries: List<SettingsSearchEntryText>,
	query: String
): List<SettingsSearchEntryGroup> =
	filteredSettingsSearchEntries(entries, query).groupedBySettingsPath()

fun filteredSettingsSearchResultItems(
	entries: List<SettingsSearchEntryText>,
	query: String
): List<SettingsSearchResultItem> =
	filteredSettingsSearchEntries(entries, query).map { entry ->
		SettingsSearchResultItem(path = entry.path, entry = entry)
	}

private fun List<SettingsSearchEntryText>.groupedBySettingsPath(): List<SettingsSearchEntryGroup> =
	linkedMapOf<String, MutableList<SettingsSearchEntryText>>().also { groups ->
		forEach { entry ->
			groups.getOrPut(entry.path) { mutableListOf() }.add(entry)
		}
	}.map { (path, entries) ->
		SettingsSearchEntryGroup(path = path, entries = entries)
	}

private fun String.searchTerms(): List<String> =
	trim()
		.lowercase()
		.split(Regex("\\s+"))
		.filter { it.isNotBlank() }

private fun SettingsSearchEntryText.searchHaystack(): String =
	buildString {
		append(path)
		append(' ')
		append(title)
		subtitle?.let {
			append(' ')
			append(it)
		}
		keywords.forEach {
			append(' ')
			append(it)
		}
	}.lowercase()
