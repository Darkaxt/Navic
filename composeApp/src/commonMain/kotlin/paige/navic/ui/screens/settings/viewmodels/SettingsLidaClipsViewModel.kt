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

class SettingsLidaClipsViewModel(
	private val repository: LidaClipsRepository
) : ViewModel() {
	private val _connectionResult = MutableStateFlow<LidaClipsConnectionResult?>(null)
	val connectionResult = _connectionResult.asStateFlow()

	private val _isTestingConnection = MutableStateFlow(false)
	val isTestingConnection = _isTestingConnection.asStateFlow()

	fun clearConnectionResult() {
		_connectionResult.value = null
	}

	fun testConnection() {
		if (_isTestingConnection.value) return

		viewModelScope.launch(Dispatchers.IO) {
			_isTestingConnection.value = true
			_connectionResult.value = repository.testConnection()
			_isTestingConnection.value = false
		}
	}
}
