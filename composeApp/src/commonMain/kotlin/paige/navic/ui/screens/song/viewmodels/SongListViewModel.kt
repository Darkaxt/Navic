package paige.navic.ui.screens.song.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongListType
import paige.navic.domain.repositories.PlaylistRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.ui.core.UiState

class SongListViewModel(
	initialListType: DomainSongListType = DomainSongListType.FrequentlyPlayed,
	private val artistId: String? = null,
	private val repository: SongRepository,
	playlistRepository: PlaylistRepository,
	private val downloadManager: DownloadManager,
	private val sessionManager: SessionManager,
	connectivityManager: ConnectivityManager
) : ViewModel() {
	private val _selectedSorting = MutableStateFlow(initialListType)
	val selectedSorting = _selectedSorting.asStateFlow()

	private val _selectedReversed = MutableStateFlow(false)
	val selectedReversed = _selectedReversed.asStateFlow()

	private val _isRefreshing = MutableStateFlow(false)
	private val _refreshError = MutableStateFlow<Exception?>(null)

	@OptIn(ExperimentalCoroutinesApi::class)
	val songsState: StateFlow<UiState<ImmutableList<DomainSong>>> =
		combine(_selectedSorting, _selectedReversed) { sorting, reversed -> sorting to reversed }
			.distinctUntilChanged()
			.flatMapLatest { (sorting, reversed) ->
				repository.songsFlow(sorting, reversed, artistId)
			}
			.combine(_refreshError) { songs, error ->
				when (error) {
					null -> UiState.Success(songs)
					else -> UiState.Error(error, songs)
				}
			}
			.combine(_isRefreshing) { state, refreshing ->
				if (refreshing) UiState.Loading(state.data) else state
			}
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading())

	@OptIn(ExperimentalCoroutinesApi::class)
	val playlistSongIds = songsState
		.map { state -> state.data.orEmpty().map { it.id }.distinct() }
		.distinctUntilChanged()
		.flatMapLatest { playlistRepository.getPlaylistSongIdsFlow(it) }
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = emptySet()
		)

	val allDownloads = downloadManager.allDownloads
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = persistentListOf()
		)

	private val _selectedSong = MutableStateFlow<DomainSong?>(null)
	val selectedSong = _selectedSong.asStateFlow()

	private val _starred = MutableStateFlow(false)
	val starred = _starred.asStateFlow()

	private val _selectedSongRating = MutableStateFlow(0)
	val selectedSongRating = _selectedSongRating.asStateFlow()

	val isOnline = connectivityManager.isOnline

	fun selectSong(song: DomainSong) {
		viewModelScope.launch(Dispatchers.IO) {
			_selectedSong.value = song
			_starred.value = repository.isSongStarred(song)
			_selectedSongRating.value = repository.getSongRating(song)
		}
	}

	fun clearSelection() {
		_selectedSong.value = null
	}

	fun refreshSongs(fullRefresh: Boolean) {
		if (fullRefresh) {
			viewModelScope.launch {
				_isRefreshing.value = true
				_refreshError.value = runCatching { repository.syncSongs() }.exceptionOrNull() as? Exception
				_isRefreshing.value = false
			}
		}
		// Non-fullRefresh is a no-op: songsState serves the reactive cache.
	}

	fun starSong(starred: Boolean) {
		viewModelScope.launch {
			val selection = _selectedSong.value ?: return@launch
			runCatching {
				if (starred) {
					repository.starSong(selection)
				} else {
					repository.unstarSong(selection)
				}
				_starred.value = starred
				refreshSongs(false)
			}
		}
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch {
			val selection = _selectedSong.value ?: return@launch
			runCatching {
				repository.rateSong(selection, rating)
				_selectedSongRating.value = rating
			}
		}
	}

	fun setSorting(sorting: DomainSongListType) {
		_selectedSorting.value = sorting
	}

	fun setReversed(reversed: Boolean) {
		_selectedReversed.value = reversed
	}

	fun clearError() {
		_refreshError.value = null
	}

	fun downloadSong(song: DomainSong) {
		downloadManager.downloadSong(song)
	}

	fun cancelDownload(songId: String) {
		downloadManager.cancelDownload(songId)
	}

	fun deleteDownload(songId: String) {
		downloadManager.deleteDownload(songId)
	}
}
