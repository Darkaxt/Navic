package paige.navic.ui.screens.aurral

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.AurralArtistSearchResult
import paige.navic.domain.repositories.AurralAlbumSearchResult
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary
import paige.navic.domain.models.AurralFlowSongIdPrefix
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainPlaylistListType
import paige.navic.domain.models.stationPlaylists
import paige.navic.domain.repositories.AurralFlowActionResult
import paige.navic.domain.repositories.AurralFlowSummary
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.domain.repositories.PlaylistRepository
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.core.UiState
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AurralHubViewModel(
	private val repository: AurralRepository,
	private val playlistRepository: PlaylistRepository
) : ViewModel() {
	private val _serviceStatus = MutableStateFlow<UiState<AurralServiceStatus?>>(UiState.Success(null))
	val serviceStatus = _serviceStatus.asStateFlow()

	private val _discovery = MutableStateFlow<UiState<AurralDiscoverySummary?>>(UiState.Success(null))
	val discovery = _discovery.asStateFlow()

	private val _artistSearchQuery = MutableStateFlow("")
	val artistSearchQuery = _artistSearchQuery.asStateFlow()

	private val _artistSearch = MutableStateFlow<UiState<AurralArtistSearchResult?>>(UiState.Success(null))
	val artistSearch = _artistSearch.asStateFlow()

	private val _albumSearch = MutableStateFlow<UiState<AurralAlbumSearchResult?>>(UiState.Success(null))
	val albumSearch = _albumSearch.asStateFlow()

	private val _flowActionState = MutableStateFlow<UiState<AurralFlowActionResult?>>(UiState.Success(null))
	val flowActionState = _flowActionState.asStateFlow()

	private val _discoverActionState = MutableStateFlow<UiState<Unit?>>(UiState.Success(null))
	val discoverActionState = _discoverActionState.asStateFlow()

	private val _activeFlowActionId = MutableStateFlow<String?>(null)
	val activeFlowActionId = _activeFlowActionId.asStateFlow()

	private val _activeDiscoverArtistId = MutableStateFlow<String?>(null)
	val activeDiscoverArtistId = _activeDiscoverArtistId.asStateFlow()

	private val _stationPlaylists = MutableStateFlow<List<DomainPlaylist>>(emptyList())
	val stationPlaylists = _stationPlaylists.asStateFlow()

	fun clearServiceStatus() {
		_serviceStatus.value = UiState.Success(null)
		_discovery.value = UiState.Success(null)
		_artistSearchQuery.value = ""
		_artistSearch.value = UiState.Success(null)
		_albumSearch.value = UiState.Success(null)
		_flowActionState.value = UiState.Success(null)
		_discoverActionState.value = UiState.Success(null)
		_activeFlowActionId.value = null
		_activeDiscoverArtistId.value = null
		_stationPlaylists.value = emptyList()
	}

	fun refreshServiceStatus() {
		if (_serviceStatus.value is UiState.Loading) return

		viewModelScope.launch(Dispatchers.IO) {
			loadServiceStatus()
		}
	}

	fun updateArtistSearchQuery(query: String) {
		_artistSearchQuery.value = query
		if (query.isBlank()) {
			_artistSearch.value = UiState.Success(null)
			_albumSearch.value = UiState.Success(null)
		}
	}

	fun searchArtists() {
		if (_artistSearch.value is UiState.Loading) return
		val query = _artistSearchQuery.value.trim()
		if (query.isEmpty()) {
			_artistSearch.value = UiState.Success(null)
			return
		}

		viewModelScope.launch(Dispatchers.IO) {
			_artistSearch.value = UiState.Loading(_artistSearch.value.data)
			val result = repository.searchArtists(query)
			_artistSearch.value = result.fold(
				onSuccess = { UiState.Success(it) },
				onFailure = { UiState.Error(Exception(it), _artistSearch.value.data) }
			)
		}
	}

	fun searchAlbums() {
		if (_albumSearch.value is UiState.Loading) return
		val query = _artistSearchQuery.value.trim()
		if (query.isEmpty()) {
			_albumSearch.value = UiState.Success(null)
			return
		}

		viewModelScope.launch(Dispatchers.IO) {
			_albumSearch.value = UiState.Loading(_albumSearch.value.data)
			val result = repository.searchAlbums(query)
			_albumSearch.value = result.fold(
				onSuccess = { UiState.Success(it) },
				onFailure = { UiState.Error(Exception(it), _albumSearch.value.data) }
			)
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

	fun playFlowDirect(
		flow: AurralFlowSummary,
		player: MediaPlayerViewModel
	) {
		if (_flowActionState.value is UiState.Loading) return

		viewModelScope.launch {
			_activeFlowActionId.value = flow.id
			_flowActionState.value = UiState.Loading(_flowActionState.value.data)
			try {
				val songs = repository.getFlowPlayableSongs(flow.id).getOrThrow()
				if (songs.isEmpty()) {
					throw IllegalStateException("Flow has no ready tracks yet")
				}
				val collection = DomainPlaylist(
					id = "$AurralFlowSongIdPrefix${flow.id}",
					name = flow.name,
					owner = "Aurral",
					comment = null,
					coverArtId = null,
					songCount = songs.size,
					duration = songs.fold(0.seconds) { total, song -> total + song.duration },
					createdAt = Instant.DISTANT_PAST,
					modifiedAt = Instant.DISTANT_PAST,
					public = null,
					readOnly = true,
					allowedUsers = emptyList(),
					validUntil = null,
					songs = songs
				)
				player.clearQueue()
				player.addToQueue(collection)
				player.playAt(0)
				_flowActionState.value = UiState.Success(null)
			} catch (error: Exception) {
				_flowActionState.value = UiState.Error(error, _flowActionState.value.data)
			} finally {
				_activeFlowActionId.value = null
			}
		}
	}

	fun monitorDiscoveredArtist(artist: AurralDiscoverArtist) {
		if (_discoverActionState.value is UiState.Loading) return

		viewModelScope.launch(Dispatchers.IO) {
			_activeDiscoverArtistId.value = artist.id
			_discoverActionState.value = UiState.Loading(_discoverActionState.value.data)
			val result = repository.monitorDiscoveredArtist(artist)
			_discoverActionState.value = result.fold(
				onSuccess = { UiState.Success(Unit) },
				onFailure = { UiState.Error(Exception(it), _discoverActionState.value.data) }
			)
			loadServiceStatus()
			_activeDiscoverArtistId.value = null
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
		loadDiscovery()
		loadStationPlaylists()
	}

	private suspend fun loadDiscovery() {
		_discovery.value = UiState.Loading(_discovery.value.data)
		val result = repository.getDiscovery()
		_discovery.value = result.fold(
			onSuccess = { UiState.Success(it) },
			onFailure = { UiState.Error(Exception(it), _discovery.value.data) }
		)
	}

	private suspend fun loadStationPlaylists() {
		val state = playlistRepository.getPlaylistsFlow(
			fullRefresh = false,
			listType = DomainPlaylistListType.Name,
			reversed = false
		).first()
		state.data?.let { playlists ->
			_stationPlaylists.value = playlists.stationPlaylists()
		}
	}

	private fun updateStationPlaylist(playlist: DomainPlaylist) {
		_stationPlaylists.value = _stationPlaylists.value.map { current ->
			if (current.id == playlist.id) playlist else current
		}
	}
}
