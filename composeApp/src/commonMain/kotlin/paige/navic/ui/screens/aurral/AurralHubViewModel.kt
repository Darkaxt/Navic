package paige.navic.ui.screens.aurral

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainPlaylistListType
import paige.navic.domain.models.stationPlaylists
import paige.navic.domain.repositories.AurralFlowActionResult
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.domain.repositories.PlaylistRepository
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.core.UiState

class AurralHubViewModel(
	private val repository: AurralRepository,
	private val playlistRepository: PlaylistRepository
) : ViewModel() {
	private val _serviceStatus = MutableStateFlow<UiState<AurralServiceStatus?>>(UiState.Success(null))
	val serviceStatus = _serviceStatus.asStateFlow()

	private val _flowActionState = MutableStateFlow<UiState<AurralFlowActionResult?>>(UiState.Success(null))
	val flowActionState = _flowActionState.asStateFlow()

	private val _activeFlowActionId = MutableStateFlow<String?>(null)
	val activeFlowActionId = _activeFlowActionId.asStateFlow()

	private val _stationPlaylists = MutableStateFlow<List<DomainPlaylist>>(emptyList())
	val stationPlaylists = _stationPlaylists.asStateFlow()

	fun clearServiceStatus() {
		_serviceStatus.value = UiState.Success(null)
		_flowActionState.value = UiState.Success(null)
		_activeFlowActionId.value = null
		_stationPlaylists.value = emptyList()
	}

	fun refreshServiceStatus() {
		if (_serviceStatus.value is UiState.Loading) return

		viewModelScope.launch(Dispatchers.IO) {
			loadServiceStatus()
		}
	}

	fun createFlow(
		name: String,
		size: Int
	) {
		runFlowAction("create") {
			repository.createFlow(name = name, size = size)
		}
	}

	fun setFlowEnabled(
		flowId: String,
		enabled: Boolean
	) {
		runFlowAction(flowId) {
			repository.setFlowEnabled(flowId = flowId, enabled = enabled)
		}
	}

	fun startFlow(
		flowId: String,
		limit: Int
	) {
		runFlowAction(flowId) {
			repository.startFlow(flowId = flowId, limit = limit)
		}
	}

	fun playStation(
		flowId: String,
		station: DomainPlaylist,
		player: MediaPlayerViewModel
	) {
		if (_flowActionState.value is UiState.Loading) return

		viewModelScope.launch {
			_activeFlowActionId.value = flowId
			_flowActionState.value = UiState.Loading(_flowActionState.value.data)
			try {
				val playableStation = playlistRepository.getPlaylistForPlayback(station)
				updateStationPlaylist(playableStation)
				if (playableStation.songs.isEmpty()) {
					throw IllegalStateException("Station has no songs yet")
				}
				player.clearQueue()
				player.addToQueue(playableStation)
				player.playAt(0)
				_flowActionState.value = UiState.Success(null)
			} catch (error: Exception) {
				_flowActionState.value = UiState.Error(error, _flowActionState.value.data)
			} finally {
				_activeFlowActionId.value = null
			}
		}
	}

	private fun runFlowAction(
		actionId: String,
		action: suspend () -> Result<AurralFlowActionResult>
	) {
		if (_flowActionState.value is UiState.Loading) return

		viewModelScope.launch(Dispatchers.IO) {
			_activeFlowActionId.value = actionId
			_flowActionState.value = UiState.Loading(_flowActionState.value.data)
			val result = action()
			_flowActionState.value = result.fold(
				onSuccess = { UiState.Success(it) },
				onFailure = { UiState.Error(Exception(it), _flowActionState.value.data) }
			)
			loadServiceStatus()
			_activeFlowActionId.value = null
		}
	}

	private suspend fun loadServiceStatus() {
		_serviceStatus.value = UiState.Loading(_serviceStatus.value.data)
		val result = repository.getServiceStatus()
		_serviceStatus.value = result.fold(
			onSuccess = { UiState.Success(it) },
			onFailure = { UiState.Error(Exception(it), _serviceStatus.value.data) }
		)
		loadStationPlaylists()
	}

	private suspend fun loadStationPlaylists() {
		playlistRepository.getPlaylistsFlow(
			fullRefresh = false,
			listType = DomainPlaylistListType.Name,
			reversed = false
		).collect { state ->
			state.data?.let { playlists ->
				_stationPlaylists.value = playlists.stationPlaylists()
			}
		}
	}

	private fun updateStationPlaylist(playlist: DomainPlaylist) {
		_stationPlaylists.value = _stationPlaylists.value.map { current ->
			if (current.id == playlist.id) playlist else current
		}
	}
}
