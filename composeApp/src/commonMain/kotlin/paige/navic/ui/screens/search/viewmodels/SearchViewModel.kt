package paige.navic.ui.screens.search.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import paige.navic.domain.models.DomainSong
import paige.navic.domain.repositories.SearchRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.ui.core.UiState
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class SearchViewModel(
	private val repository: SearchRepository,
	private val songRepository: SongRepository,
	connectivityManager: ConnectivityManager,
	downloadManager: DownloadManager,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	private val _searchState = MutableStateFlow<UiState<List<Any>>>(UiState.Success(emptyList()))
	val searchState = _searchState.asStateFlow()

	private val _searchHistory = MutableStateFlow<List<String>>(
		decodeSearchHistory(preferenceManager.searchHistoryEntries)
	)
	val searchHistory = _searchHistory.asStateFlow()

	private val _selectedSong = MutableStateFlow<DomainSong?>(null)
	val selectedSong = _selectedSong.asStateFlow()

	private val _selectedSongIsStarred = MutableStateFlow(false)
	val selectedSongIsStarred = _selectedSongIsStarred.asStateFlow()

	private val _selectedSongRating = MutableStateFlow(0)
	val selectedSongRating = _selectedSongRating.asStateFlow()

	val searchQuery = TextFieldState()

	val isOnline = connectivityManager.isOnline
	val downloadedSongs = downloadManager.downloadedSongs

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			snapshotFlow { searchQuery.text }
				.debounce(300.milliseconds)
				.collectLatest { queryText ->
					val query = queryText.toString()
					if (query.isBlank()) {
						_searchState.value = UiState.Success(emptyList())
					} else {
						_searchState.value = UiState.Loading()
						try {
							_searchState.value = UiState.Success(repository.search(query))
						} catch (e: Exception) {
							if (e !is CancellationException) {
								_searchState.value = UiState.Error(e)
							}
						}
					}
				}
		}
	}

	fun addToSearchHistory(query: String) {
		setSearchHistory(
			updatedSearchHistoryAfterSubmit(
				query = query,
				history = _searchHistory.value,
				pauseSearchHistory = preferenceManager.pauseSearchHistory
			)
		)
	}

	fun removeFromSearchHistory(query: String) {
		setSearchHistory(
			updatedSearchHistoryAfterRemoval(
				query = query,
				history = _searchHistory.value
			)
		)
	}

	fun clearSearchHistory() {
		setSearchHistory(emptyList())
	}

	private fun setSearchHistory(history: List<String>) {
		_searchHistory.value = history
		preferenceManager.searchHistoryEntries = encodeSearchHistory(history)
	}

	fun selectSong(song: DomainSong) {
		viewModelScope.launch {
			_selectedSong.value = song
			_selectedSongIsStarred.value = songRepository.isSongStarred(song)
			_selectedSongRating.value = songRepository.getSongRating(song)
		}
	}

	fun starSelectedSong(starred: Boolean) {
		viewModelScope.launch {
			val selection = _selectedSong.value ?: return@launch
			runCatching {
				if (starred) {
					songRepository.starSong(selection)
				} else {
					songRepository.unstarSong(selection)
				}
				_selectedSongIsStarred.value = starred
			}
		}
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch {
			val selection = _selectedSong.value ?: return@launch
			runCatching {
				songRepository.rateSong(selection, rating)
				_selectedSongRating.value = rating
			}
		}
	}

	fun clearSelectedSong() {
		_selectedSong.value = null
	}
}
