package paige.navic.ui.screens.playlist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.DbRepository
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.playlistIdsToRefreshAfterMembershipUpdate
import paige.navic.ui.core.UiState
import paige.navic.util.core.Logger
import dev.zt64.subsonic.api.model.Playlist as ApiPlaylist

class PlaylistUpdateDialogViewModel(
	private val songs: List<DomainSong>,
	private val playlistToExclude: String?,
	private val sessionManager: SessionManager,
	private val dbRepository: DbRepository
) : ViewModel() {
	private val _playlistsState = MutableStateFlow<UiState<List<ApiPlaylist>>>(UiState.Loading())
	val playlistsState = _playlistsState.asStateFlow()

	private val _confirmState = MutableStateFlow<UiState<Nothing?>>(UiState.Success(null))
	val confirmState = _confirmState.asStateFlow()

	private val _selectedPlaylists = MutableStateFlow<Set<ApiPlaylist>>(emptySet())
	val selectedPlaylists = _selectedPlaylists.asStateFlow()

	private val _events = Channel<Event>()
	val events = _events.receiveAsFlow()

	init {
		refreshResults()
	}

	fun refreshResults() {
		viewModelScope.launch {
			_selectedPlaylists.value = emptySet()
			_playlistsState.value = UiState.Loading()
			try {
				val results = sessionManager.withApi { it.getPlaylists() }
				_playlistsState.value =
					UiState.Success(results.filter { it.id != playlistToExclude })
			} catch (e: Exception) {
				_playlistsState.value = UiState.Error(e)
			}
		}
	}

	fun togglePlaylistSelection(playlist: ApiPlaylist) {
		_selectedPlaylists.value = if (playlist in _selectedPlaylists.value) {
			_selectedPlaylists.value - playlist
		} else {
			_selectedPlaylists.value + playlist
		}
	}

	fun confirm() {
		viewModelScope.launch {
			_confirmState.value = UiState.Loading()
			try {
				val selectedPlaylists = _selectedPlaylists.value
				selectedPlaylists.forEach { playlist ->
					sessionManager.withApi { api -> api.updatePlaylist(
						playlist.id,
						songIdsToAdd = songs.map { it.id }
					) }
				}
				playlistIdsToRefreshAfterMembershipUpdate(selectedPlaylists.map { it.id })
					.forEach { playlistId ->
						dbRepository.syncPlaylistSongs(playlistId)
							.onFailure { error ->
								Logger.w(
									"PlaylistUpdateDialogViewModel",
									"Failed to refresh playlist $playlistId after update",
									error
								)
							}
					}
				_confirmState.value = UiState.Success(null)
				_events.send(Event.Dismiss)
			} catch (e: Exception) {
				_confirmState.value = UiState.Error(e)
			}
		}
	}

	enum class Event {
		Dismiss
	}
}
