package paige.navic.ui.screens.settings.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.LidaClipsConnectionResult
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.domain.repositories.LidaClipsServiceStatus
import paige.navic.ui.core.UiState

class SettingsLidaClipsViewModel(
	private val repository: LidaClipsRepository
) : ViewModel() {
	private val _connectionResult = MutableStateFlow<LidaClipsConnectionResult?>(null)
	val connectionResult = _connectionResult.asStateFlow()

	private val _isTestingConnection = MutableStateFlow(false)
	val isTestingConnection = _isTestingConnection.asStateFlow()

	private val _serviceStatus = MutableStateFlow<UiState<LidaClipsServiceStatus?>>(UiState.Success(null))
	val serviceStatus = _serviceStatus.asStateFlow()

	private val _isUpdatingSyncPaused = MutableStateFlow(false)
	val isUpdatingSyncPaused = _isUpdatingSyncPaused.asStateFlow()

	fun clearConnectionResult() {
		_connectionResult.value = null
	}

	fun clearServiceStatus() {
		_serviceStatus.value = UiState.Success(null)
	}

	fun testConnection() {
		if (_isTestingConnection.value) return

		viewModelScope.launch(Dispatchers.IO) {
			_isTestingConnection.value = true
			_connectionResult.value = repository.testConnection()
			_isTestingConnection.value = false
		}
	}

	fun refreshServiceStatus() {
		if (_serviceStatus.value is UiState.Loading) return

		viewModelScope.launch(Dispatchers.IO) {
			_serviceStatus.value = UiState.Loading(_serviceStatus.value.data)
			val result = repository.getServiceStatus()
			_serviceStatus.value = result.fold(
				onSuccess = { UiState.Success(it) },
				onFailure = { UiState.Error(Exception(it), _serviceStatus.value.data) }
			)
		}
	}

	fun setSyncPaused(syncPaused: Boolean) {
		if (_isUpdatingSyncPaused.value) return

		viewModelScope.launch(Dispatchers.IO) {
			_isUpdatingSyncPaused.value = true
			try {
				_serviceStatus.value = UiState.Loading(_serviceStatus.value.data)
				val result = repository.setSyncPaused(syncPaused)
				_serviceStatus.value = result.fold(
					onSuccess = { UiState.Success(it) },
					onFailure = { UiState.Error(Exception(it), _serviceStatus.value.data) }
				)
			} finally {
				_isUpdatingSyncPaused.value = false
			}
		}
	}
}
