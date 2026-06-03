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
	private val collectionArtworkResolver = BinderyCollectionArtworkResolver(repository, viewModelScope)
	val collectionArtworkByPath = collectionArtworkResolver.artworkByPath

	fun refreshCatalog(fullRefresh: Boolean) {
		catalogJob?.cancel()
		if (fullRefresh) {
			collectionArtworkResolver.clear()
		}
		catalogJob = viewModelScope.launch {
			val currentData = _catalogState.value.data
			if (fullRefresh || currentData == null) {
				_catalogState.value = UiState.Loading(currentData)
			}
			repository.getCatalog(path).fold(
				onSuccess = { catalog ->
					_catalogState.value = UiState.Success(catalog)
				},
				onFailure = { error ->
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
		collectionArtworkResolver.clear()
		_catalogState.value = UiState.Success(BinderyCatalog(title = ""))
	}

	fun resolveCollectionArtwork(card: BinderyCatalogCard.Link) {
		collectionArtworkResolver.resolve(card)
	}

	fun clearError() {
		_catalogState.value = _catalogState.value.data?.let { UiState.Success(it) }
			?: UiState.Loading()
	}
}
