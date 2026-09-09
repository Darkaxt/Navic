package paige.navic.ui.screens.search.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import paige.navic.domain.models.DomainSong
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.SearchRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.data.remote.aurral.configuredAurralBaseUrl
import paige.navic.ui.screens.search.MusicSearchState
import paige.navic.ui.screens.search.MusicSearchSource
import paige.navic.ui.screens.search.musicSearchStates
import paige.navic.ui.screens.search.eligibleMusicSearchRequest

class SearchViewModel(
	private val repository: SearchRepository,
	private val aurralRepository: AurralRepository,
	private val songRepository: SongRepository,
	connectivityManager: ConnectivityManager,
	downloadManager: DownloadManager,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	private val _searchState = MutableStateFlow(MusicSearchState())
	val searchState = _searchState.asStateFlow()
	private val retryRevision = MutableStateFlow(0L)

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
			musicSearchStates(
				queries = combine(snapshotFlow { searchQuery.text.toString() }, retryRevision) { query, _ -> query }
			) { query ->
				buildMap {
					put(MusicSearchSource.Library) { repository.searchLocal(query) }
					if (isOnline.value) put(MusicSearchSource.Navidrome, eligibleMusicSearchRequest({ isOnline.value }) {
						repository.searchRemote(query)
					})
					if (shouldSearchAurral()) {
						put(MusicSearchSource.AurralArtists, eligibleMusicSearchRequest(::shouldSearchAurral) {
							aurralRepository.searchArtists(query).getOrThrow().artists
						})
						put(MusicSearchSource.AurralAlbums, eligibleMusicSearchRequest(::shouldSearchAurral) {
							aurralRepository.searchAlbums(query).getOrThrow().albums
						})
					}
				}
			}.collect { result ->
				if (result.query == searchQuery.text.toString().trim()) _searchState.value = result
			}
		}
	}

	fun retrySearch() {
		retryRevision.value++
	}

	private fun shouldSearchAurral(): Boolean =
		isOnline.value &&
			preferenceManager.aurralEnabled &&
			configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) != null

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
		viewModelScope.launch(Dispatchers.IO) {
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

	fun setInitialQuery(query: String) {
		val normalized = query.trim()
		if (normalized.isEmpty() || searchQuery.text.toString() == normalized) return
		searchQuery.clearText()
		searchQuery.edit {
			insert(0, normalized)
		}
	}
}
