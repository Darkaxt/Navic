package paige.navic.ui.screens.playlist.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainPlaylistListType
import paige.navic.domain.repositories.PlaylistRepository
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.core.UiState

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistListViewModel(
	private val repository: PlaylistRepository,
	@Suppress("UNUSED_PARAMETER") sessionManager: SessionManager
) : ViewModel() {
	private val _selectedSorting = MutableStateFlow(DomainPlaylistListType.DateAdded)
	val selectedSorting = _selectedSorting.asStateFlow()

	private val _selectedReversed = MutableStateFlow(false)
	val selectedReversed = _selectedReversed.asStateFlow()

	private val _isRefreshing = MutableStateFlow(false)
	private val _refreshError = MutableStateFlow<Exception?>(null)
	private val _playbackLoading = MutableStateFlow(false)

	/** Reactive playlist state derived from the shared repository cache. Auto-loads on
	 *  first subscriber and re-derives when sorting/reversed change; no per-visit re-query. */
	val playlistsState: StateFlow<UiState<ImmutableList<DomainPlaylist>>> =
		combine(_selectedSorting, _selectedReversed) { sorting, reversed -> sorting to reversed }
			.distinctUntilChanged()
			.flatMapLatest { (sorting, reversed) ->
				repository.playlistsFlow(sorting, reversed)
			}
			.combine(_refreshError) { playlists, error ->
				when (error) {
					null -> UiState.Success(playlists)
					else -> UiState.Error(error, playlists)
				}
			}
			.combine(_isRefreshing) { state, refreshing ->
				if (refreshing) UiState.Loading(state.data) else state
			}
			.combine(_playbackLoading) { state, loading ->
				if (loading) UiState.Loading(state.data) else state
			}
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading())

	private val _selectedPlaylist = MutableStateFlow<DomainPlaylist?>(null)
	val selectedPlaylist = _selectedPlaylist.asStateFlow()

	val gridState = LazyGridState()

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
		if (fullRefresh) {
			viewModelScope.launch {
				_isRefreshing.value = true
				_refreshError.value = runCatching { repository.syncPlaylists() }.exceptionOrNull() as? Exception
				_isRefreshing.value = false
			}
		}
		// Non-fullRefresh is a no-op: playlistsState serves the reactive cache.
	}

	private fun withSelectedPlaylistForPlayback(action: (DomainPlaylist) -> Unit) {
		val selectedPlaylist = _selectedPlaylist.value ?: return
		viewModelScope.launch {
			_playbackLoading.value = true
			try {
				val playablePlaylist = repository.getPlaylistForPlayback(selectedPlaylist)
				_selectedPlaylist.value = playablePlaylist
				action(playablePlaylist)
			} catch (error: Exception) {
				_refreshError.value = error
			} finally {
				_playbackLoading.value = false
			}
		}
	}

	fun setSorting(sorting: DomainPlaylistListType) {
		_selectedSorting.value = sorting
	}

	fun setReversed(reversed: Boolean) {
		_selectedReversed.value = reversed
	}

	fun clearError() {
		_refreshError.value = null
	}
}
