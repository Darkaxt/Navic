package paige.navic.ui.screens.settings.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.LastFmConnectionResult
import paige.navic.domain.repositories.LastFmRepository
import paige.navic.domain.repositories.LastFmServiceStatus
import paige.navic.ui.core.UiState

class SettingsLastFmViewModel(
	private val repository: LastFmRepository
) : ViewModel() {
	private val _connectionResult = MutableStateFlow<LastFmConnectionResult?>(null)
	val connectionResult = _connectionResult.asStateFlow()

	private val _isTestingConnection = MutableStateFlow(false)
	val isTestingConnection = _isTestingConnection.asStateFlow()

	private val _serviceStatus = MutableStateFlow<UiState<LastFmServiceStatus?>>(UiState.Success(null))
	val serviceStatus = _serviceStatus.asStateFlow()

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
}
