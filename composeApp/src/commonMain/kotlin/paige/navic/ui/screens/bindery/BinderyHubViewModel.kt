package paige.navic.ui.screens.bindery

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.ui.core.UiState

data class BinderyHubState(
	val root: BinderyCatalog,
	val rows: List<BinderyHubCatalogRow>
)

data class BinderyHubCatalogRow(
	val row: BinderyHubRow,
	val catalog: BinderyCatalog
) {
	fun cards(languageFilter: String? = null): List<BinderyCatalogCard> =
		binderyCatalogCards(catalog, row.catalogTab)
			.let { cards ->
				if (row.kind.showOnlyAvailableContent()) {
					cards.filter { card -> card.hasAvailableContent(languageFilter) }
				} else {
					cards
				}
			}
}

private fun BinderyHubRowKind.showOnlyAvailableContent(): Boolean =
	when (this) {
		BinderyHubRowKind.LastRead,
		BinderyHubRowKind.RecentlyAdded,
		BinderyHubRowKind.MostPopular,
		BinderyHubRowKind.Audiobooks -> true
		BinderyHubRowKind.Genres,
		BinderyHubRowKind.Authors,
		BinderyHubRowKind.Collections,
		BinderyHubRowKind.Findings,
		BinderyHubRowKind.Wanted -> false
	}

class BinderyHubViewModel(
	private val repository: BinderyRepository
) : ViewModel() {
	private val _hubState = MutableStateFlow<UiState<BinderyHubState>>(UiState.Loading())
	val hubState = _hubState.asStateFlow()

	val gridState = LazyGridState()
	private var hubJob: Job? = null
	private val collectionArtworkResolver = BinderyCollectionArtworkResolver(repository, viewModelScope)
	val collectionArtworkByPath = collectionArtworkResolver.artworkByPath

	fun refreshHub(
		fullRefresh: Boolean,
		languageFilter: String? = null
	) {
		hubJob?.cancel()
		if (fullRefresh) {
			collectionArtworkResolver.clear()
		}
		hubJob = viewModelScope.launch {
			val currentData = _hubState.value.data
			val cachedData = if (!fullRefresh && currentData == null) {
				loadCachedHub(languageFilter)
			} else {
				null
			}
			if (cachedData != null) {
				_hubState.value = UiState.Success(cachedData)
			}
			val visibleData = cachedData ?: currentData
			if (fullRefresh || visibleData == null) {
				_hubState.value = UiState.Loading(visibleData)
			}
			loadHub(languageFilter).fold(
				onSuccess = { state ->
					val currentState = _hubState.value
					if (currentState !is UiState.Success || currentState.data != state) {
						_hubState.value = UiState.Success(state)
					}
				},
				onFailure = { error ->
					_hubState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = visibleData
					)
				}
			)
		}
	}

	fun clearHub() {
		hubJob?.cancel()
		hubJob = null
		collectionArtworkResolver.clear()
		_hubState.value = UiState.Success(
			BinderyHubState(
				root = BinderyCatalog(title = ""),
				rows = emptyList()
			)
		)
	}

	fun clearError() {
		_hubState.value = _hubState.value.data?.let { UiState.Success(it) }
			?: UiState.Loading()
	}

	fun resolveCollectionArtwork(card: BinderyCatalogCard.Link) {
		collectionArtworkResolver.resolve(card)
	}

	private suspend fun loadCachedHub(languageFilter: String?): BinderyHubState? {
		val rootCatalog = repository.getCachedCatalog("/").getOrNull() ?: return null
		val rows = binderyHubRows(rootCatalog).mapNotNull { row ->
			repository.getCachedCatalog(
				binderyAvailabilityFilteredCatalogPath(
					path = row.catalogPath,
					languageFilter = languageFilter,
					mode = BinderyAvailabilityQueryMode.List
				)
			).getOrNull()?.let { catalog ->
				BinderyHubCatalogRow(row, catalog)
			}
		}
		return BinderyHubState(
			root = rootCatalog,
			rows = rows
		)
	}

	private suspend fun loadHub(languageFilter: String?): Result<BinderyHubState> {
		val rootResult = repository.getCatalog("/")
		return rootResult.fold(
			onSuccess = { rootCatalog ->
				runCatching {
					val rows = binderyHubRows(rootCatalog)
					val catalogs = coroutineScope {
						rows.map { row ->
							async {
								repository.getCatalog(
									binderyAvailabilityFilteredCatalogPath(
										path = row.catalogPath,
										languageFilter = languageFilter,
										mode = BinderyAvailabilityQueryMode.List
									)
								).getOrNull()?.let { catalog ->
									BinderyHubCatalogRow(row, catalog)
								}
							}
						}.awaitAll().filterNotNull()
					}
					BinderyHubState(
						root = rootCatalog,
						rows = catalogs
					)
				}
			},
			onFailure = { error ->
				Result.failure(error)
			}
		)
	}
}
