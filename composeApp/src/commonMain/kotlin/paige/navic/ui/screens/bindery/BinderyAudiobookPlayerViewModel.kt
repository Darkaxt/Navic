package paige.navic.ui.screens.bindery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.ui.core.UiState

class BinderyAudiobookPlayerViewModel(
	private val bookId: String,
	private val repository: BinderyRepository
) : ViewModel() {
	private val _manifestState = MutableStateFlow<UiState<BinderyManifest>>(UiState.Loading())
	val manifestState = _manifestState.asStateFlow()

	private var manifestJob: Job? = null

	fun refreshManifest(fullRefresh: Boolean = false) {
		manifestJob?.cancel()
		manifestJob = viewModelScope.launch {
			val currentData = _manifestState.value.data
			if (fullRefresh || currentData == null) {
				_manifestState.value = UiState.Loading(currentData)
			}
			repository.getManifest(bookId).fold(
				onSuccess = { manifest ->
					_manifestState.value = UiState.Success(manifest)
				},
				onFailure = { error ->
					_manifestState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = currentData
					)
				}
			)
		}
	}

	fun clearError() {
		_manifestState.value = _manifestState.value.data?.let { UiState.Success(it) } ?: UiState.Loading()
	}
}
