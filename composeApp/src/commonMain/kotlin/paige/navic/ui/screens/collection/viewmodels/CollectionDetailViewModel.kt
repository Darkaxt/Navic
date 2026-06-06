package paige.navic.ui.screens.collection.viewmodels

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainAlbumInfo
import paige.navic.domain.models.IntegrationService
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.repositories.AlbumRepository
import paige.navic.domain.repositories.AurralAcquisitionQueueItem
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralAlbumTrackItem
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.CollectionRepository
import paige.navic.domain.repositories.PlaylistRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.util.core.Logger
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.collection.aurralAlbumRecoveryCandidate
import paige.navic.ui.screens.collection.aurralAlbumRecoveryCandidateChoices
import paige.navic.ui.screens.collection.aurralAlbumRecoveryQueries
import paige.navic.ui.screens.collection.aurralAlbumRecoveryRows as buildAurralAlbumRecoveryRows
import paige.navic.ui.screens.collection.AurralAlbumRecoveryTrack
import paige.navic.ui.screens.collection.AurralAlbumRecoveryTrackRow

class CollectionDetailViewModel(
	private val collectionId: String,
	private val repository: CollectionRepository,
	private val songRepository: SongRepository,
	private val albumRepository: AlbumRepository,
	private val aurralRepository: AurralRepository,
	playlistRepository: PlaylistRepository,
	private val downloadManager: DownloadManager,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	connectivityManager: ConnectivityManager
) : ViewModel() {
	private val _collectionState = MutableStateFlow<UiState<DomainSongCollection>>(
		runBlocking {
			try {
				UiState.Loading(repository.getLocalData(collectionId))
			} catch (_: Exception) {
				UiState.Loading()
			}
		}
	)
	val collectionState: StateFlow<UiState<DomainSongCollection>> = _collectionState.asStateFlow()
	@OptIn(ExperimentalCoroutinesApi::class)
	val playlistSongIds = collectionState
		.map { state -> state.data?.songs.orEmpty().map { it.id }.distinct() }
		.distinctUntilChanged()
		.flatMapLatest { playlistRepository.getPlaylistSongIdsFlow(it) }
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = emptySet()
		)

	private val _starred = MutableStateFlow(false)
	val starred = _starred.asStateFlow()

	val isOnline = connectivityManager.isOnline

	val allDownloads = downloadManager.allDownloads
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = emptyList()
		)

	val otherAlbums = (_collectionState.value.data as? DomainAlbum)?.let { album ->
		repository.getOtherAlbums(album.artistId, album.id)
	}?.stateIn(
		scope = viewModelScope,
		started = SharingStarted.Lazily,
		initialValue = emptyList()
	) ?: MutableStateFlow(emptyList())

	private val _selectedSong = MutableStateFlow<DomainSong?>(null)
	val selectedSong: StateFlow<DomainSong?> = _selectedSong.asStateFlow()

	private val _albumInfoState = MutableStateFlow<UiState<DomainAlbumInfo>>(UiState.Loading())
	val albumInfoState = _albumInfoState.asStateFlow()

	private val _selectedSongIsStarred = MutableStateFlow(false)
	val selectedSongIsStarred = _selectedSongIsStarred.asStateFlow()

	private val _selectedSongRating = MutableStateFlow(0)
	val selectedSongRating = _selectedSongRating.asStateFlow()

	private val _selectedAlbum = MutableStateFlow<DomainAlbum?>(null)
	val selectedAlbum: StateFlow<DomainAlbum?> = _selectedAlbum.asStateFlow()

	private val _selectedAlbumIsStarred = MutableStateFlow(false)
	val selectedAlbumIsStarred = _selectedAlbumIsStarred.asStateFlow()

	private val _selectedAlbumRating = MutableStateFlow(0)
	val selectedAlbumRating = _selectedAlbumRating.asStateFlow()

	private val _aurralAlbumRequests = MutableStateFlow<List<AurralAlbumRequest>>(emptyList())
	val aurralAlbumRequests = _aurralAlbumRequests.asStateFlow()

	private val _aurralAlbumRecoveryMatch = MutableStateFlow<AurralAlbumSearchItem?>(null)
	val aurralAlbumRecoveryMatch = _aurralAlbumRecoveryMatch.asStateFlow()
	private val _aurralAlbumRecoveryRows = MutableStateFlow<List<AurralAlbumRecoveryTrackRow>>(emptyList())
	val aurralAlbumRecoveryRows = _aurralAlbumRecoveryRows.asStateFlow()
	private val _aurralAlbumRecoveryLoading = MutableStateFlow(false)
	val aurralAlbumRecoveryLoading = _aurralAlbumRecoveryLoading.asStateFlow()
	private val _aurralAlbumRecoveryCandidates = MutableStateFlow<List<AurralAlbumSearchItem>>(emptyList())
	val aurralAlbumRecoveryCandidates = _aurralAlbumRecoveryCandidates.asStateFlow()
	private var aurralAlbumRecoveryKey: String? = null

	private val _rating = MutableStateFlow(0)
	val rating = _rating.asStateFlow()

	val listState = LazyListState()

	private val integrationEnabledListenerRemovers = mutableListOf<() -> Unit>()

	init {
		integrationEnabledListenerRemovers += preferenceManager.addIntegrationEnabledChangeListener(IntegrationService.Aurral) { enabled ->
			if (!enabled) {
				_aurralAlbumRequests.value = emptyList()
				_aurralAlbumRecoveryMatch.value = null
				_aurralAlbumRecoveryRows.value = emptyList()
				_aurralAlbumRecoveryLoading.value = false
				_aurralAlbumRecoveryCandidates.value = emptyList()
				aurralAlbumRecoveryKey = null
			}
		}
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshCollection(false) }
		}
	}

	override fun onCleared() {
		integrationEnabledListenerRemovers.forEach { removeListener -> removeListener() }
		integrationEnabledListenerRemovers.clear()
		super.onCleared()
	}

	fun refreshCollection(fullRefresh: Boolean) {
		refreshAurralAcquisitionRequests()
		viewModelScope.launch {
			repository.getCollectionFlow(fullRefresh, collectionId).collect {
				_collectionState.value = it
				if (it.data is DomainAlbum) {
					val album = it.data as DomainAlbum
					_starred.value = albumRepository.isAlbumStarred(album)
					_rating.value = albumRepository.getAlbumRating(album)
					try {
						val albumInfo = repository.getAlbumInfo(collectionId)
						_albumInfoState.value = UiState.Success(albumInfo.toDomainModel())
					} catch (e: Exception) {
						_albumInfoState.value = UiState.Error(e)
					}
					refreshAurralAlbumRecovery(album)
				} else {
					_aurralAlbumRecoveryMatch.value = null
					_aurralAlbumRecoveryRows.value = emptyList()
					_aurralAlbumRecoveryLoading.value = false
					_aurralAlbumRecoveryCandidates.value = emptyList()
					aurralAlbumRecoveryKey = null
				}
			}
		}
	}

	private suspend fun refreshAurralAlbumRecovery(album: DomainAlbum) {
		val recoveryKey = buildString {
			append(album.id)
			append('|')
			append(album.name)
			append('|')
			append(album.artistName)
			append('|')
			append(album.songs.joinToString("|") { song ->
				"${song.id}:${song.title}:${song.musicBrainzId}:${song.discNumber}:${song.trackNumber}:${song.duration}"
			})
		}
		if (aurralAlbumRecoveryKey == recoveryKey) return
		aurralAlbumRecoveryKey = recoveryKey
		_aurralAlbumRecoveryMatch.value = null
		_aurralAlbumRecoveryRows.value = emptyList()
		_aurralAlbumRecoveryLoading.value = false
		_aurralAlbumRecoveryCandidates.value = emptyList()
		if (!preferenceManager.aurralEnabled) return
		_aurralAlbumRecoveryLoading.value = true
		val candidates = mutableListOf<AurralAlbumSearchItem>()
		val candidateIds = mutableSetOf<String>()
		var match: AurralAlbumSearchItem? = null
		for (query in aurralAlbumRecoveryQueries(album)) {
			aurralRepository.searchAlbums(query, limit = 8)
				.onFailure { error ->
					Logger.w(
						"CollectionDetailViewModel",
						"Failed to resolve Aurral recovery album for ${album.name} with query $query",
						error
					)
				}
				.getOrNull()
				?.albums
				.orEmpty()
				.forEach { candidate ->
					val key = candidate.id.trim().lowercase()
					if (key.isNotEmpty() && candidateIds.add(key)) candidates += candidate
				}
			match = aurralAlbumRecoveryCandidate(
				album = album,
				candidates = candidates
			)
			if (match != null) break
		}
		_aurralAlbumRecoveryMatch.value = match
		if (match != null) {
			_aurralAlbumRecoveryCandidates.value = emptyList()
			loadAurralAlbumRecoveryTracks(album, match)
		} else {
			_aurralAlbumRecoveryCandidates.value = aurralAlbumRecoveryCandidateChoices(
				album = album,
				candidates = candidates
			)
				.take(5)
				.map { it.album }
		}
		_aurralAlbumRecoveryLoading.value = false
	}

	private suspend fun loadAurralAlbumRecoveryTracks(
		album: DomainAlbum,
		match: AurralAlbumSearchItem
	) {
		aurralRepository.getAlbumTracks(match)
			.onSuccess { tracks ->
				_aurralAlbumRecoveryRows.value = buildAurralAlbumRecoveryRows(
					album = album,
					tracks = tracks.map(AurralAlbumTrackItem::toRecoveryTrack)
				)
			}
			.onFailure { error ->
				_aurralAlbumRecoveryRows.value = emptyList()
				Logger.w(
					"CollectionDetailViewModel",
					"Failed to load Aurral recovery tracks for ${album.name}",
					error
				)
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

	fun selectSong(song: DomainSong) {
		viewModelScope.launch {
			_selectedSong.value = song
			_selectedSongIsStarred.value = songRepository.isSongStarred(song)
			_selectedSongRating.value = songRepository.getSongRating(song)
		}
	}

	fun selectAlbum(album: DomainAlbum) {
		viewModelScope.launch {
			_selectedAlbum.value = album
			_selectedAlbumIsStarred.value = albumRepository.isAlbumStarred(album)
			_selectedAlbumRating.value = albumRepository.getAlbumRating(album)
		}
	}

	fun clearSelection() {
		_selectedSong.value = null
		_selectedAlbum.value = null
	}

	fun clearError() {
		_collectionState.value.data?.let {
			_collectionState.value = UiState.Success(it)
		}
	}

	fun requestAurralRecoveryAlbum() {
		val album = _aurralAlbumRecoveryMatch.value ?: return
		viewModelScope.launch {
			aurralRepository.requestAlbum(album)
				.onSuccess {
					refreshAurralAcquisitionRequests()
				}
				.onFailure { error ->
					_collectionState.value = UiState.Error(
						error as? Exception ?: Exception(error),
						_collectionState.value.data
					)
				}
		}
	}

	fun selectAurralRecoveryCandidate(candidate: AurralAlbumSearchItem) {
		val album = _collectionState.value.data as? DomainAlbum ?: return
		viewModelScope.launch {
			_aurralAlbumRecoveryMatch.value = candidate
			_aurralAlbumRecoveryCandidates.value = emptyList()
			_aurralAlbumRecoveryRows.value = emptyList()
			_aurralAlbumRecoveryLoading.value = true
			loadAurralAlbumRecoveryTracks(album, candidate)
			_aurralAlbumRecoveryLoading.value = false
		}
	}

	fun removeFromPlaylist() {
		val song = _selectedSong.value ?: return
		val songs = _collectionState.value.data?.songs ?: return
		viewModelScope.launch {
			try {
				sessionManager.api.updatePlaylist(
					id = collectionId,
					songIndicesToRemove = listOf(songs.indexOf(song))
				)
				refreshCollection(true)
			} catch (e: Exception) {
				Logger.e("CollectionDetailViewModel", "Failed to remove song from playlist", e)
			}
		}
		clearSelection()
	}

	fun starSelectedSong() {
		viewModelScope.launch {
			val selection = _selectedSong.value ?: return@launch
			runCatching {
				songRepository.starSong(selection)
				_selectedSongIsStarred.value = true
			}
		}
	}

	fun unstarSelectedSong() {
		viewModelScope.launch {
			val selection = _selectedSong.value ?: return@launch
			runCatching {
				songRepository.unstarSong(selection)
				_selectedSongIsStarred.value = false
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

	fun rateAlbum(rating: Int) {
		viewModelScope.launch {
			(_collectionState.value.data as? DomainAlbum)?.let { album ->
				albumRepository.rateAlbum(album, rating)
				_rating.value = rating
			}
		}
	}

	fun starAlbum(starred: Boolean) {
		viewModelScope.launch {
			runCatching {
				val collection = _collectionState.value.data ?: return@launch
				if (collection !is DomainAlbum) return@launch
				if (starred) {
					albumRepository.starAlbum(collection)
				} else {
					albumRepository.unstarAlbum(collection)
				}
				refreshCollection(false)
			}
		}
	}

	fun rateSelectedAlbum(rating: Int) {
		viewModelScope.launch {
			_selectedAlbum.value?.let { album ->
				albumRepository.rateAlbum(album, rating)
				_selectedAlbumRating.value = rating
			}
		}
	}

	fun starSelectedAlbum(starred: Boolean) {
		viewModelScope.launch {
			runCatching {
				val collection = _selectedAlbum.value ?: return@launch
				if (starred) {
					albumRepository.starAlbum(collection)
				} else {
					albumRepository.unstarAlbum(collection)
				}
				_selectedAlbumIsStarred.value = starred
			}
		}
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

	fun downloadAll() {
		val collection = _collectionState.value.data ?: return
		viewModelScope.launch {
			downloadManager.downloadCollection(collection)
		}
	}

	fun cancelDownloadAll() {
		_collectionState.value.data?.let { downloadManager.cancelCollectionDownload(it) }
	}

	fun collectionDownloadStatus(): Flow<DownloadStatus> {
		val songs = _collectionState.value.data?.songs.orEmpty()
		return downloadManager.getCollectionDownloadStatus(songs.map { it.id })
	}
}

private fun AurralAcquisitionQueueItem.toAlbumRequest() = AurralAlbumRequest(
	albumMbid = albumMbid,
	albumName = albumName,
	artistMbid = artistMbid,
	artistName = artistName,
	status = status
)

private fun AurralAlbumTrackItem.toRecoveryTrack() = AurralAlbumRecoveryTrack(
	id = id,
	title = title,
	recordingMbid = recordingMbid,
	discNumber = discNumber,
	trackNumber = trackNumber,
	durationMs = durationMs,
	previewUrl = previewUrl,
	status = status,
	requested = requested
)
