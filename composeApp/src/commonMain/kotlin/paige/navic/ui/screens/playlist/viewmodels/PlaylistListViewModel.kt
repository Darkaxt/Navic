package paige.navic.ui.screens.playlist.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainPlaylistListType
import paige.navic.domain.repositories.PlaylistRepository
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.core.UiState

class PlaylistListViewModel(
	private val repository: PlaylistRepository,
	private val sessionManager: SessionManager
) : ViewModel() {
	private val _playlistsState =
		MutableStateFlow<UiState<ImmutableList<DomainPlaylist>>>(UiState.Loading())
	val playlistsState = _playlistsState.asStateFlow()

	private val _selectedPlaylist = MutableStateFlow<DomainPlaylist?>(null)
	val selectedPlaylist = _selectedPlaylist.asStateFlow()

	private val _selectedSorting = MutableStateFlow(DomainPlaylistListType.DateAdded)
	val selectedSorting = _selectedSorting.asStateFlow()

	private val _selectedReversed = MutableStateFlow(false)
	val selectedReversed = _selectedReversed.asStateFlow()

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshPlaylists(false) }
		}
	}

	fun selectPlaylist(playlist: DomainPlaylist) {
		_selectedPlaylist.value = playlist
	}

	fun clearSelection() {
		_selectedPlaylist.value = null
	}

	fun playSelectedPlaylistNext(player: MediaPlayerViewModel) {
		withSelectedPlaylistForPlayback { playlist ->
			player.playNext(playlist)
		}
	}

	fun addSelectedPlaylistToQueue(player: MediaPlayerViewModel) {
		withSelectedPlaylistForPlayback { playlist ->
			player.addToQueue(playlist)
		}
	}

	fun refreshPlaylists(fullRefresh: Boolean) {
		viewModelScope.launch {
			repository.getPlaylistsFlow(
				fullRefresh,
				_selectedSorting.value,
				_selectedReversed.value
			).collect {
				_playlistsState.value = it
			}
		}
	}

	private fun withSelectedPlaylistForPlayback(
		action: (DomainPlaylist) -> Unit
	) {
		val selectedPlaylist = _selectedPlaylist.value ?: return
		viewModelScope.launch {
			val currentData = _playlistsState.value.data
			if (currentData != null) {
				_playlistsState.value = UiState.Loading(currentData)
			}

			try {
				val playablePlaylist = repository.getPlaylistForPlayback(selectedPlaylist)
				updateSelectedPlaylist(playablePlaylist)
				action(playablePlaylist)
			} catch (error: Exception) {
				_playlistsState.value = UiState.Error(
					error = error,
					data = _playlistsState.value.data ?: currentData ?: persistentListOf()
				)
			}
		}
	}

	private fun updateSelectedPlaylist(playlist: DomainPlaylist) {
		_selectedPlaylist.value = playlist
		val currentState = _playlistsState.value
		val currentData = currentState.data ?: return
		val updatedData = currentData
			.map { if (it.id == playlist.id) playlist else it }
			.toImmutableList()

		_playlistsState.value = when (currentState) {
			is UiState.Error -> UiState.Error(currentState.error, updatedData)
			is UiState.Loading,
			is UiState.Success -> UiState.Success(updatedData)
		}
	}

	fun setSorting(sorting: DomainPlaylistListType) {
		_selectedSorting.value = sorting
		refreshPlaylists(false)
	}

	fun setReversed(reversed: Boolean) {
		_selectedReversed.value = reversed
		refreshPlaylists(false)
	}

	fun clearError() {
		_playlistsState.value = UiState.Success(_playlistsState.value.data ?: persistentListOf())
	}
}
