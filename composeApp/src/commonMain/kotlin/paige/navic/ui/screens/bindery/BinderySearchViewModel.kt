package paige.navic.ui.screens.bindery

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.ui.core.UiState
import kotlin.time.Duration.Companion.milliseconds

private const val BINDERY_SEARCH_BOOK_LIMIT = 200

@OptIn(FlowPreview::class)
class BinderySearchViewModel(
	private val repository: BinderyRepository,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	private val _searchState = MutableStateFlow<UiState<List<BinderySearchResult>>>(UiState.Success(emptyList()))
	val searchState = _searchState.asStateFlow()
	private val _actionError = MutableStateFlow<Throwable?>(null)
	val actionError = _actionError.asStateFlow()
	private val _actionInFlight = MutableStateFlow<Set<String>>(emptySet())
	val actionInFlight = _actionInFlight.asStateFlow()

	val searchQuery = TextFieldState()
	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			snapshotFlow { searchQuery.text }
				.debounce(300.milliseconds)
				.collectLatest { queryText ->
					val query = queryText.toString()
					if (query.isBlank()) {
						_searchState.value = UiState.Success(emptyList())
					} else {
						_searchState.value = UiState.Loading()
						try {
							_searchState.value = UiState.Success(searchBindery(query))
						} catch (e: Exception) {
							if (e !is CancellationException) {
								_searchState.value = UiState.Error(e)
							}
						}
					}
				}
		}
	}

	private suspend fun searchBindery(query: String): List<BinderySearchResult> = coroutineScope {
		val terms = query.searchTerms()
		val encodedQuery = query.trim().encodeURLParameter()
		val languageFilter = normalizedBinderyLanguageFilter(preferenceManager.binderyLanguageFilter)
		val books = async {
			repository.getCatalog(
				binderySearchCatalogPath(
					path = "/opds/search?q=$encodedQuery&limit=$BINDERY_SEARCH_BOOK_LIMIT",
					languageFilter = languageFilter
				)
			)
		}
		val collections = async {
			repository.getCatalog(
				binderySearchCatalogPath(
					path = BinderyCatalogTab.Collections.path,
					languageFilter = languageFilter
				)
			)
		}
		val authors = async {
			repository.getCatalog(
				binderyDiscoverAuthorsPath(query)
			)
		}
		val results = listOf(
			books.await().toSearchResults(BinderyCatalogTab.Books, terms),
			collections.await().toSearchResults(BinderyCatalogTab.Collections, terms),
			authors.await().toSearchResults(BinderyCatalogTab.Authors, terms)
		)
		val failures = results.mapNotNull { result -> result.exceptionOrNull() }
		val successes = results.mapNotNull { result -> result.getOrNull() }.flatten()
		if (successes.isEmpty() && failures.isNotEmpty()) {
			throw failures.first() as? Exception ?: Exception(failures.first())
		}
		successes
	}

	fun performAction(link: BinderyLink) {
		val actionPath = link.href.trim().takeIf { it.isNotEmpty() } ?: return
		if (actionPath in _actionInFlight.value) return
		viewModelScope.launch {
			_actionInFlight.value = _actionInFlight.value + actionPath
			repository.performAction(actionPath).fold(
				onSuccess = {
					_actionInFlight.value = _actionInFlight.value - actionPath
					val query = searchQuery.text.toString()
					if (query.isNotBlank()) {
						_searchState.value = UiState.Loading(_searchState.value.data)
						runCatching { searchBindery(query) }.fold(
							onSuccess = { results -> _searchState.value = UiState.Success(results) },
							onFailure = { error ->
								_searchState.value = UiState.Error(
									error = error as? Exception ?: Exception(error),
									data = _searchState.value.data
								)
							}
						)
					}
				},
				onFailure = { error ->
					_actionInFlight.value = _actionInFlight.value - actionPath
					_actionError.value = error
				}
			)
		}
	}

	fun clearActionError() {
		_actionError.value = null
	}
}

data class BinderySearchResult(
	val tab: BinderyCatalogTab,
	val card: BinderyCatalogCard
)

enum class BinderySearchCategory {
	All,
	Books,
	Collections,
	Authors
}

fun binderySearchResultsForCategory(
	results: List<BinderySearchResult>,
	category: BinderySearchCategory
): List<BinderySearchResult> =
	when (category) {
		BinderySearchCategory.All -> results
		BinderySearchCategory.Books -> results.filter { it.tab == BinderyCatalogTab.Books }
		BinderySearchCategory.Collections -> results.filter { it.tab == BinderyCatalogTab.Collections }
		BinderySearchCategory.Authors -> results.filter { it.tab == BinderyCatalogTab.Authors }
	}

private fun Result<BinderyCatalog>.toSearchResults(
	tab: BinderyCatalogTab,
	terms: List<String>
): Result<List<BinderySearchResult>> =
	map { catalog ->
		binderyCatalogCards(catalog, tab)
			.filter { card -> card.matchesTerms(terms) }
			.map { card -> BinderySearchResult(tab = tab, card = card) }
	}

private fun String.searchTerms(): List<String> =
	trim()
		.lowercase()
		.split(Regex("\\s+"))
		.filter { it.isNotBlank() }

private fun BinderyCatalogCard.matchesTerms(terms: List<String>): Boolean {
	if (terms.isEmpty()) return false
	val haystack = when (this) {
		is BinderyCatalogCard.Book -> listOfNotNull(title, subtitle)
		is BinderyCatalogCard.Link -> listOfNotNull(title, subtitle) + properties.values
	}.joinToString(separator = " ").lowercase()
	return terms.all { term -> haystack.contains(term) }
}
