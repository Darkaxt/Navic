package paige.navic.ui.screens.bindery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.BinderyAudiobookVersion
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.ui.core.UiState

class BinderyAudiobookDetailViewModel(
	private val audiobookId: String,
	private val repository: BinderyRepository
) : ViewModel() {
	private val _detailState = MutableStateFlow<UiState<BinderyAudiobookVersion>>(UiState.Loading())
	val detailState = _detailState.asStateFlow()

	private var detailJob: Job? = null

	fun refresh(fullRefresh: Boolean = false) {
		detailJob?.cancel()
		detailJob = viewModelScope.launch {
			val currentData = _detailState.value.data
			if (fullRefresh || currentData == null) {
				_detailState.value = UiState.Loading(currentData)
			}
			repository.getAudiobookDetail(audiobookId).fold(
				onSuccess = { detail ->
					_detailState.value = UiState.Success(detail)
				},
				onFailure = { error ->
					_detailState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = currentData
					)
				}
			)
		}
	}

	fun clearError() {
		_detailState.value = _detailState.value.data?.let { UiState.Success(it) } ?: UiState.Loading()
	}
}
