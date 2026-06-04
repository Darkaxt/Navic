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
	val cards: List<BinderyCatalogCard>
		get() = binderyCatalogCards(catalog, row.catalogTab)
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
			if (fullRefresh || currentData == null) {
				_hubState.value = UiState.Loading(currentData)
			}
			loadHub(languageFilter).fold(
				onSuccess = { state ->
					_hubState.value = UiState.Success(state)
				},
				onFailure = { error ->
					_hubState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = currentData
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
