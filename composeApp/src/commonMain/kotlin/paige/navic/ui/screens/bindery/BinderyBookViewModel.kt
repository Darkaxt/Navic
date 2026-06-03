package paige.navic.ui.screens.bindery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.ui.core.UiState

data class BinderyBookData(
	val manifest: BinderyManifest,
	val resources: BinderyResourceCatalog = BinderyResourceCatalog(title = "Resources")
)

class BinderyBookViewModel(
	private val bookId: String,
	private val repository: BinderyRepository
) : ViewModel() {
	private val _bookState = MutableStateFlow<UiState<BinderyBookData>>(UiState.Loading())
	val bookState = _bookState.asStateFlow()

	private var bookJob: Job? = null

	fun refreshBook(fullRefresh: Boolean) {
		bookJob?.cancel()
		bookJob = viewModelScope.launch {
			val currentData = _bookState.value.data
			if (fullRefresh || currentData == null) {
				_bookState.value = UiState.Loading(currentData)
			}
			repository.getManifest(bookId).fold(
				onSuccess = { manifest ->
					repository.getBookResources(bookId).fold(
						onSuccess = { resources ->
							_bookState.value = UiState.Success(BinderyBookData(manifest, resources))
						},
						onFailure = { error ->
							_bookState.value = UiState.Error(
								error = error as? Exception ?: Exception(error),
								data = BinderyBookData(manifest)
							)
						}
					)
				},
				onFailure = { error ->
					_bookState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = currentData
					)
				}
			)
		}
	}

	fun clearBook() {
		bookJob?.cancel()
		bookJob = null
		_bookState.value = UiState.Success(
			BinderyBookData(
				manifest = BinderyManifest(
					id = bookId,
					title = ""
				)
			)
		)
	}

	fun clearError() {
		_bookState.value = _bookState.value.data?.let { UiState.Success(it) } ?: UiState.Loading()
	}
}
