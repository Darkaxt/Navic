package paige.navic.ui.screens.album.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainAlbumListType
import paige.navic.domain.repositories.AlbumRepository
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralRepository
import paige.navic.ui.core.UiState

@OptIn(ExperimentalCoroutinesApi::class)
open class AlbumListViewModel(
	initialListType: DomainAlbumListType = DomainAlbumListType.Year,
	private val repository: AlbumRepository,
	private val sessionManager: SessionManager,
	private val aurralRepository: AurralRepository
) : ViewModel(), KoinComponent {
	private val _albumsState =
		MutableStateFlow<UiState<ImmutableList<DomainAlbum>>>(UiState.Loading())
	val albumsState = _albumsState.asStateFlow()

	private val _aurralAlbumRequests = MutableStateFlow<List<AurralAlbumRequest>>(emptyList())
	val aurralAlbumRequests = _aurralAlbumRequests.asStateFlow()

	private val _selectedAlbum = MutableStateFlow<DomainAlbum?>(null)
	val selectedAlbum = _selectedAlbum.asStateFlow()

	private val _starred = MutableStateFlow(false)
	val starred = _starred.asStateFlow()

	private val _rating = MutableStateFlow(0)
	val rating = _rating.asStateFlow()

	private val _listType = MutableStateFlow(initialListType)
	val listType = _listType.asStateFlow()

	private val _selectedReversed = MutableStateFlow(false)
	val selectedReversed = _selectedReversed.asStateFlow()

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshAlbums(false) }
		}
	}

	fun refreshAlbums(fullRefresh: Boolean) {
		refreshAurralAcquisitionRequests()
		viewModelScope.launch {
			repository.getAlbumsFlow(fullRefresh, _listType.value, _selectedReversed.value)
				.collect {
					_albumsState.value = it
				}
		}
	}

	private fun refreshAurralAcquisitionRequests() {
		viewModelScope.launch {
			aurralRepository.getServiceStatus()
				.onSuccess { status ->
					_aurralAlbumRequests.value = status.acquisitionQueue.map { it.toAlbumRequest() }
				}
				.onFailure {
					_aurralAlbumRequests.value = emptyList()
				}
		}
	}

	fun selectAlbum(album: DomainAlbum) {
		viewModelScope.launch {
			_selectedAlbum.value = album
			_starred.value = repository.isAlbumStarred(album)
			_rating.value = repository.getAlbumRating(album)
		}
	}

	fun clearSelection() {
		_selectedAlbum.value = null
	}

	fun starAlbum(starred: Boolean) {
		viewModelScope.launch {
			val selection = _selectedAlbum.value ?: return@launch
			runCatching {
				if (starred) {
					repository.starAlbum(selection)
				} else {
					repository.unstarAlbum(selection)
				}
				_starred.value = starred
			}
		}
	}

	fun setRating(rating: Int) {
		viewModelScope.launch {
			val selection = _selectedAlbum.value ?: return@launch
			runCatching {
				_rating.value = rating
				repository.rateAlbum(selection, rating)
			}
		}
	}

	fun setListType(listType: DomainAlbumListType) {
		_listType.value = listType
		refreshAlbums(false)
	}

	fun setReversed(reversed: Boolean) {
		_selectedReversed.value = reversed
		refreshAlbums(false)
	}

	fun clearError() {
		_albumsState.value = UiState.Success(_albumsState.value.data ?: persistentListOf())
	}
}

private fun AurralAcquisitionQueueItem.toAlbumRequest() = AurralAlbumRequest(
	albumMbid = albumMbid,
	albumName = albumName,
	artistMbid = artistMbid,
	artistName = artistName,
	status = status
)
