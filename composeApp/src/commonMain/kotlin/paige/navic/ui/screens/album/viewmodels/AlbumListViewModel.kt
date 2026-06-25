package paige.navic.ui.screens.album.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
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
import org.koin.core.component.KoinComponent
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainAlbumListType
import paige.navic.domain.models.IntegrationService
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.AlbumRepository
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralRepository
import paige.navic.ui.core.UiState

@OptIn(ExperimentalCoroutinesApi::class)
open class AlbumListViewModel(
	initialListType: DomainAlbumListType = DomainAlbumListType.Year,
	private val repository: AlbumRepository,
	private val sessionManager: SessionManager,
	private val aurralRepository: AurralRepository,
	private val preferenceManager: PreferenceManager
) : ViewModel(), KoinComponent {
	private val _listType = MutableStateFlow(initialListType)
	val listType = _listType.asStateFlow()

	private val _selectedReversed = MutableStateFlow(false)
	val selectedReversed = _selectedReversed.asStateFlow()

	private val _isRefreshing = MutableStateFlow(false)
	private val _refreshError = MutableStateFlow<Exception?>(null)

	/** Reactive album state derived from the shared repository cache. Auto-loads on first
	 *  subscriber and re-derives when listType/reversed change; no per-visit Room re-query. */
	val albumsState: StateFlow<UiState<ImmutableList<DomainAlbum>>> =
		combine(_listType, _selectedReversed) { listType, reversed -> listType to reversed }
			.distinctUntilChanged()
			.flatMapLatest { (listType, reversed) ->
				repository.albumsFlow(listType, reversed)
			}
			.combine(_refreshError) { albums, error ->
				when (error) {
					null -> UiState.Success(albums)
					else -> UiState.Error(error, albums)
				}
			}
			.combine(_isRefreshing) { state, refreshing ->
				if (refreshing) UiState.Loading(state.data) else state
			}
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading())

	private val _aurralAlbumRequests = MutableStateFlow<List<AurralAlbumRequest>>(emptyList())
	val aurralAlbumRequests = _aurralAlbumRequests.asStateFlow()

	private val _selectedAlbum = MutableStateFlow<DomainAlbum?>(null)
	val selectedAlbum = _selectedAlbum.asStateFlow()

	private val _starred = MutableStateFlow(false)
	val starred = _starred.asStateFlow()

	private val _rating = MutableStateFlow(0)
	val rating = _rating.asStateFlow()

	val gridState = LazyGridState()

	private val integrationEnabledListenerRemovers = mutableListOf<() -> Unit>()

	init {
		integrationEnabledListenerRemovers += preferenceManager.addIntegrationEnabledChangeListener(IntegrationService.Aurral) { enabled ->
			if (!enabled) {
				_aurralAlbumRequests.value = emptyList()
			}
		}
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshAurralAcquisitionRequests() }
		}
	}

	override fun onCleared() {
		integrationEnabledListenerRemovers.forEach { removeListener -> removeListener() }
		integrationEnabledListenerRemovers.clear()
		super.onCleared()
	}

	fun refreshAlbums(fullRefresh: Boolean) {
		refreshAurralAcquisitionRequests()
		if (fullRefresh) {
			viewModelScope.launch {
				_isRefreshing.value = true
				_refreshError.value = runCatching { repository.syncAlbums() }.exceptionOrNull() as? Exception
				_isRefreshing.value = false
			}
		}
		// Non-fullRefresh is a no-op: albumsState already serves the reactive cache.
	}

	private fun refreshAurralAcquisitionRequests() {
		viewModelScope.launch(Dispatchers.IO) {
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
	}

	fun setReversed(reversed: Boolean) {
		_selectedReversed.value = reversed
	}

	fun clearError() {
		_refreshError.value = null
	}
}

private fun AurralAcquisitionQueueItem.toAlbumRequest() = AurralAlbumRequest(
	albumMbid = albumMbid,
	albumName = albumName,
	artistMbid = artistMbid,
	artistName = artistName,
	status = status
)
