package paige.navic.ui.screens.bindery

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.ui.core.UiState

class BinderyCatalogViewModel(
	private val path: String,
	private val repository: BinderyRepository
) : ViewModel() {
	private val _catalogState = MutableStateFlow<UiState<BinderyCatalog>>(UiState.Loading())
	val catalogState = _catalogState.asStateFlow()

	val gridState = LazyGridState()
	private var catalogJob: Job? = null
	private var nextPageJob: Job? = null
	private var nextPagePath: String? = null
	private val _isLoadingNextPage = MutableStateFlow(false)
	val isLoadingNextPage = _isLoadingNextPage.asStateFlow()
	private val _hasNextPage = MutableStateFlow(false)
	val hasNextPage = _hasNextPage.asStateFlow()
	private val collectionArtworkResolver = BinderyCollectionArtworkResolver(repository, viewModelScope)
	val collectionArtworkByPath = collectionArtworkResolver.artworkByPath

	fun refreshCatalog(fullRefresh: Boolean) {
		catalogJob?.cancel()
		nextPageJob?.cancel()
		_isLoadingNextPage.value = false
		if (fullRefresh) {
			collectionArtworkResolver.clear()
		}
		catalogJob = viewModelScope.launch {
			val currentData = _catalogState.value.data
			if (fullRefresh || currentData == null) {
				_catalogState.value = UiState.Loading(currentData)
			}
			repository.getCatalog(binderyInitialCatalogPath(path)).fold(
				onSuccess = { catalog ->
					nextPagePath = catalog.nextPagePath()
					_hasNextPage.value = nextPagePath != null
					_catalogState.value = UiState.Success(catalog)
				},
				onFailure = { error ->
					nextPagePath = currentData?.nextPagePath()
					_hasNextPage.value = nextPagePath != null
					_catalogState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = currentData
					)
				}
			)
		}
	}

	fun clearCatalog() {
		catalogJob?.cancel()
		catalogJob = null
		nextPageJob?.cancel()
		nextPageJob = null
		nextPagePath = null
		_isLoadingNextPage.value = false
		_hasNextPage.value = false
		collectionArtworkResolver.clear()
		_catalogState.value = UiState.Success(BinderyCatalog(title = ""))
	}

	fun loadNextPage() {
		if (_isLoadingNextPage.value) return
		val requestedPath = nextPagePath ?: return
		_isLoadingNextPage.value = true
		nextPageJob = viewModelScope.launch {
			val currentData = _catalogState.value.data
			repository.getCatalog(requestedPath).fold(
				onSuccess = { nextPage ->
					val merged = (currentData ?: BinderyCatalog(title = nextPage.title))
						.appendCatalogPage(nextPage)
					nextPagePath = merged.nextPagePath()
					_hasNextPage.value = nextPagePath != null
					_isLoadingNextPage.value = false
					_catalogState.value = UiState.Success(merged)
				},
				onFailure = { error ->
					nextPagePath = requestedPath
					_hasNextPage.value = false
					_isLoadingNextPage.value = false
					_catalogState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = currentData
					)
				}
			)
		}
	}

	fun resolveCollectionArtwork(card: BinderyCatalogCard.Link) {
		collectionArtworkResolver.resolve(card)
	}

	fun clearError() {
		_catalogState.value = _catalogState.value.data?.let { UiState.Success(it) }
			?: UiState.Loading()
	}
}
