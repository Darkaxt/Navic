package paige.navic.ui.screens.artist.viewmodels

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.AurralMissingAlbumRow
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralSimilarArtistRow
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.models.aurralMissingAlbumRows
import paige.navic.domain.models.aurralSimilarArtistRows
import paige.navic.domain.models.sortedByAlbumYearDescending
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AlbumRepository
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.ArtistRepository
import paige.navic.domain.repositories.DbRepository
import paige.navic.domain.repositories.LastFmRepository
import paige.navic.domain.repositories.PlaylistRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.DownloadManager
import paige.navic.util.core.Logger
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.artist.artistDetailPlaybackOrigin
import paige.navic.ui.screens.artist.artistLastFmTopTrackSongs
import paige.navic.ui.screens.aurral.AurralArtistIdentity
import paige.navic.ui.screens.aurral.aurralArtistIdentityCandidatesForLocalArtist
import paige.navic.ui.screens.aurral.aurralRecommendedAlbumsForArtist

@Immutable
data class ArtistState(
	val artist: DomainArtist,
	val albums: List<DomainAlbum>,
	val topSongs: List<DomainSong>,
	val lastFmTopSongs: List<DomainSong> = emptyList(),
	val similarArtists: List<DomainArtist> = emptyList(),
	val aurralAlbumRequests: List<AurralAlbumRequest> = emptyList(),
	val aurralMissingAlbums: List<AurralMissingAlbumRow> = emptyList(),
	val aurralRecommendedAlbums: List<AurralAlbumSearchItem> = emptyList(),
	val aurralSimilarArtists: List<AurralSimilarArtistRow> = emptyList(),
	val aurralPreviewTracks: List<AurralPreviewTrack> = emptyList(),
	val aurralMonitored: Boolean? = null,
	val aurralArtistMbid: String? = null,
	val aurralArtistName: String? = null,
	val aurralArtistImageUrl: String? = null,
	val aurralLoading: Boolean = false,
	val aurralError: String? = null
)

class ArtistDetailViewModel(
	private val artistId: String,
	private val repository: DbRepository,
	private val artistRepository: ArtistRepository,
	private val songRepository: SongRepository,
	private val albumRepository: AlbumRepository,
	private val aurralRepository: AurralRepository,
	private val lastFmRepository: LastFmRepository,
	playlistRepository: PlaylistRepository,
	private val artistDao: ArtistDao,
	private val albumDao: AlbumDao,
	private val downloadManager: DownloadManager,
	connectivityManager: ConnectivityManager
) : ViewModel() {
	private val _artistState = MutableStateFlow<UiState<ArtistState>>(UiState.Loading())
	val artistState = _artistState.asStateFlow()
	@OptIn(ExperimentalCoroutinesApi::class)
	val playlistSongIds = artistState
		.map { state ->
			(state as? UiState.Success)?.data?.let { data ->
				(data.topSongs + data.lastFmTopSongs).map { it.id }.distinct()
			}.orEmpty()
		}
		.distinctUntilChanged()
		.flatMapLatest { playlistRepository.getPlaylistSongIdsFlow(it) }
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = emptySet()
		)

	private val _starred = MutableStateFlow(false)
	val starred = _starred.asStateFlow()

	private val _selectedSong = MutableStateFlow<DomainSong?>(null)
	val selectedSong = _selectedSong.asStateFlow()

	private val _selectedSongIsStarred = MutableStateFlow(false)
	val selectedSongIsStarred = _selectedSongIsStarred.asStateFlow()

	private val _selectedSongRating = MutableStateFlow(0)
	val selectedSongRating = _selectedSongRating.asStateFlow()

	private val _selectedAlbum = MutableStateFlow<DomainAlbum?>(null)
	val selectedAlbum = _selectedAlbum.asStateFlow()

	private val _selectedAlbumIsStarred = MutableStateFlow(false)
	val selectedAlbumIsStarred = _selectedAlbumIsStarred.asStateFlow()

	private val _selectedAlbumRating = MutableStateFlow(0)
	val selectedAlbumRating = _selectedAlbumRating.asStateFlow()

	private val _monitoringInAurral = MutableStateFlow(false)
	val monitoringInAurral = _monitoringInAurral.asStateFlow()

	val isOnline = connectivityManager.isOnline

	val allDownloads = downloadManager.allDownloads
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = emptyList()
		)

	val scrollState = ScrollState(initial = 0)

	init {
		loadArtistData()
		viewModelScope.launch {
			aurralRepository.artistStateRevision.drop(1).collect {
				refreshAurralEnrichment()
			}
		}
	}

	private fun loadArtistData() {
		viewModelScope.launch {
			try {
				val artistEntity = artistDao.getArtistById(artistId)
					?: throw Exception("Artist not found in database")
				val domainArtist = artistEntity.toDomainModel()

				var albumsWithSongs =
					albumDao.getAlbumsByArtist(artistId).firstOrNull() ?: emptyList()

				if (albumsWithSongs.isEmpty()) {
					albumsWithSongs = albumDao.getAlbumsByArtistName(domainArtist.name).firstOrNull() ?: emptyList()
				}

				val domainAlbums = albumsWithSongs
					.map { it.toDomainModel() }
					.sortedByAlbumYearDescending()

				val allArtistSongs = albumsWithSongs.flatMap { it.songs }
					.map { it.toDomainModel() }

				val domainSongs = allArtistSongs
					.sortedByDescending { it.playCount }
					.take(12)

				val initialSimilarArtists = domainArtist.similarArtistIds.mapNotNull { id ->
					artistDao.getArtistById(id)?.toDomainModel()
				}

				_starred.value = artistRepository.isArtistStarred(domainArtist)

				_artistState.value = UiState.Success(
					ArtistState(
						artist = domainArtist,
						albums = domainAlbums,
						topSongs = domainSongs,
						similarArtists = initialSimilarArtists
					)
				)
				loadLastFmTopTracks(domainArtist, allArtistSongs)
				loadAurralEnrichment(domainArtist, domainAlbums)

				repository.fetchArtistMetadata(artistId)
					.onSuccess { updatedArtist ->
						val currentState = (_artistState.value as? UiState.Success)?.data
						if (currentState != null) {
							val shouldRefreshAurral =
								updatedArtist.musicBrainzId != currentState.artist.musicBrainzId

							val updatedSimilarArtists =
								updatedArtist.similarArtistIds.mapNotNull { id ->
									artistDao.getArtistById(id)?.toDomainModel()
								}

							_artistState.value = UiState.Success(
								currentState.copy(
									artist = updatedArtist,
									similarArtists = updatedSimilarArtists
								)
							)
							loadLastFmTopTracks(
								artist = updatedArtist,
								localSongs = currentState.albums.flatMap { it.songs }
							)
							if (shouldRefreshAurral) {
								loadAurralEnrichment(updatedArtist, currentState.albums)
							}
						}
					}
					.onFailure { error ->
						Logger.e("ArtistDetailViewModel", "Failed to fetch artist metadata", error)
					}
			} catch (e: Exception) {
				_artistState.value = UiState.Error(e)
			}
		}
	}

	private fun loadLastFmTopTracks(
		artist: DomainArtist,
		localSongs: List<DomainSong>
	) {
		viewModelScope.launch {
			lastFmRepository.getArtistTopTracks(
				artistName = artist.name,
				artistMbid = artist.musicBrainzId
			).onSuccess { tracks ->
				val latestState = (_artistState.value as? UiState.Success)?.data ?: return@onSuccess
				if (latestState.artist.id != artist.id) return@onSuccess
				_artistState.value = UiState.Success(
					latestState.copy(
						lastFmTopSongs = artistLastFmTopTrackSongs(
							tracks = tracks,
							localSongs = localSongs
						)
					)
				)
			}.onFailure { error ->
				Logger.w("ArtistDetailViewModel", "Failed to fetch Last.fm top tracks", error)
			}
		}
	}

	private fun loadAurralEnrichment(
		artist: DomainArtist,
		albums: List<DomainAlbum>
	) {
		viewModelScope.launch {
			val currentState = (_artistState.value as? UiState.Success)?.data ?: return@launch
			_artistState.value = UiState.Success(
				currentState.copy(
					aurralLoading = true,
					aurralError = null
				)
			)

			val primaryAurralArtist = artist.musicBrainzId
				?.trim()
				?.takeIf { it.isNotEmpty() }
				?.let { mbid ->
					artist.copy(
						name = artist.name.trim().takeIf { it.isNotEmpty() } ?: mbid,
						musicBrainzId = mbid
					)
				}
			val primaryEnrichmentDeferred = primaryAurralArtist?.let { aurralArtist ->
				async { aurralRepository.getArtistEnrichment(aurralArtist) }
			}
			val discoveryDeferred = async {
				aurralRepository.getDiscovery(hydrateMissingImages = false).getOrNull()
			}
			var primaryEnrichmentResult: Result<AurralArtistEnrichment?>? = primaryEnrichmentDeferred
				?.await()
				?.also { result ->
					val monitored = result.getOrNull()?.monitored
					if (monitored == true) {
						val latestState = (_artistState.value as? UiState.Success)?.data
						if (latestState != null) {
							_artistState.value = UiState.Success(
								latestState.copy(
									aurralMonitored = true,
									aurralArtistMbid = primaryAurralArtist.musicBrainzId,
									aurralArtistName = primaryAurralArtist.name,
									aurralError = null
								)
							)
						}
					}
				}
			val discovery = discoveryDeferred.await()
			val aurralIdentities = discovery
				?.let { summary -> aurralArtistIdentityCandidatesForLocalArtist(summary, artist) }
				.orEmpty()
				.ifEmpty {
					artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }?.let { mbid ->
						listOf(
							AurralArtistIdentity(
								mbid = mbid,
								name = artist.name.trim().takeIf { it.isNotEmpty() } ?: mbid
							)
						)
					}.orEmpty()
				}
			val aurralArtistCandidates = aurralIdentities.map { identity ->
				identity to artist.copy(
					name = identity.name,
					musicBrainzId = identity.mbid
				)
			}

			if (aurralArtistCandidates.isEmpty()) {
				val latestState = (_artistState.value as? UiState.Success)?.data ?: return@launch
				_artistState.value = UiState.Success(
					latestState.copy(
						aurralLoading = false,
						aurralError = null
					)
				)
				return@launch
			}

			var selectedCandidate = aurralArtistCandidates.first()
			var aurralArtist = selectedCandidate.second
			var enrichmentResult = primaryEnrichmentResult
				?.takeIf { primaryAurralArtist?.musicBrainzId == aurralArtist.musicBrainzId }
				?: aurralRepository.getArtistEnrichment(aurralArtist)
			if (enrichmentResult.isFailure || enrichmentResult.getOrNull()?.monitored == null) {
				for (candidate in aurralArtistCandidates.drop(1)) {
					val candidateArtist = candidate.second
					val candidateResult = aurralRepository.getArtistEnrichment(candidateArtist)
					val currentMonitoring = enrichmentResult.getOrNull()?.monitored
					val candidateMonitoring = candidateResult.getOrNull()?.monitored
					val shouldUseCandidate = when {
						candidateResult.isFailure -> false
						enrichmentResult.isFailure -> true
						currentMonitoring == null && candidateMonitoring != null -> true
						enrichmentResult.getOrNull() == null && candidateResult.getOrNull() != null -> true
						else -> false
					}
					if (shouldUseCandidate) {
						selectedCandidate = candidate
						aurralArtist = candidateArtist
						enrichmentResult = candidateResult
					}
					if (enrichmentResult.isSuccess && enrichmentResult.getOrNull()?.monitored != null) {
						break
					}
				}
			}
			if (enrichmentResult.isSuccess) {
				val enrichment = enrichmentResult.getOrNull()
				val verifiedAurralArtistImageUrl = selectedCandidate.first.imageUrl
					?.trim()
					?.takeIf { it.isNotEmpty() }
					?: aurralIdentities.firstNotNullOfOrNull { identity ->
						identity.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
					}
				val localArtists = artistDao.getAllArtistsList().map { it.toDomainModel() }
				val missingAlbumRows = enrichment
					?.let { aurralMissingAlbumRows(it, albums) }
					.orEmpty()
					.let { rows -> resolveAurralMissingAlbumCovers(aurralArtist, rows) }
				val recommendedAlbums = discovery
					?.let { discovery ->
						aurralRecommendedAlbumsForArtist(
							discovery = discovery,
							artistMbid = aurralArtist.musicBrainzId,
							artistName = aurralArtist.name
						)
					}
					.orEmpty()
				val latestState = (_artistState.value as? UiState.Success)?.data ?: return@launch
				_artistState.value = UiState.Success(
					latestState.copy(
						aurralMissingAlbums = missingAlbumRows,
						aurralRecommendedAlbums = recommendedAlbums,
						aurralAlbumRequests = enrichment?.requests.orEmpty(),
						aurralSimilarArtists = enrichment
							?.let {
								aurralSimilarArtistRows(
									enrichment = it,
									allLocalArtists = localArtists,
									localSimilarArtists = latestState.similarArtists
								)
							}
							.orEmpty(),
						aurralPreviewTracks = enrichment?.previewTracks.orEmpty(),
						aurralMonitored = enrichment?.monitored,
						aurralArtistMbid = aurralArtist.musicBrainzId,
						aurralArtistName = aurralArtist.name,
						aurralArtistImageUrl = verifiedAurralArtistImageUrl,
						aurralLoading = false,
						aurralError = null
					)
				)
			} else {
				val error = enrichmentResult.exceptionOrNull()
					?: IllegalStateException("Aurral artist enrichment failed")
				Logger.w("ArtistDetailViewModel", "Failed to fetch Aurral artist enrichment", error)
				val latestState = (_artistState.value as? UiState.Success)?.data ?: return@launch
				_artistState.value = UiState.Success(
					latestState.copy(
						aurralLoading = false,
						aurralError = error.message ?: error::class.simpleName
					)
				)
			}
		}
	}

	fun refreshAurralEnrichment() {
		val currentState = (_artistState.value as? UiState.Success)?.data ?: return
		loadAurralEnrichment(currentState.artist, currentState.albums)
	}

	private suspend fun resolveAurralMissingAlbumCovers(
		artist: DomainArtist,
		rows: List<AurralMissingAlbumRow>
	): List<AurralMissingAlbumRow> = coroutineScope {
		rows.map { row ->
			async {
				if (!row.coverUrl.isNullOrBlank()) {
					row
				} else {
					aurralRepository.getReleaseGroupCoverImageUrl(row.releaseGroup, artist.name)
						.getOrNull()
						?.let { coverUrl -> row.copy(coverUrl = coverUrl) }
						?: row
				}
			}
		}.awaitAll()
	}

	fun selectSong(song: DomainSong) {
		viewModelScope.launch {
			_selectedSong.value = song
			_selectedSongIsStarred.value = songRepository.isSongStarred(song)
			_selectedSongRating.value = songRepository.getSongRating(song)
		}
	}

	fun clearSelection() {
		_selectedSong.value = null
	}

	fun selectAlbum(album: DomainAlbum) {
		viewModelScope.launch {
			_selectedAlbum.value = album
			_selectedAlbumIsStarred.value = albumRepository.isAlbumStarred(album)
			_selectedAlbumRating.value = albumRepository.getAlbumRating(album)
		}
	}

	fun rateSelectedAlbum(rating: Int) {
		viewModelScope.launch {
			val selection = _selectedAlbum.value ?: return@launch
			runCatching {
				_selectedAlbumRating.value = rating
				albumRepository.rateAlbum(selection, rating)
			}
		}
	}

	fun clearAlbumSelection() {
		_selectedAlbum.value = null
	}

	fun starSelectedSong() {
		viewModelScope.launch {
			val selection = _selectedSong.value ?: return@launch
			runCatching {
				_selectedSongIsStarred.value = true
				songRepository.starSong(selection)
			}
		}
	}

	fun unstarSelectedSong() {
		viewModelScope.launch {
			val selection = _selectedSong.value ?: return@launch
			runCatching {
				_selectedSongIsStarred.value = false
				songRepository.unstarSong(selection)
			}
		}
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch {
			val selection = _selectedSong.value ?: return@launch
			runCatching {
				_selectedSongRating.value = rating
				songRepository.rateSong(selection, rating)
			}
		}
	}

	fun starArtist(starred: Boolean) {
		val artist = (_artistState.value as? UiState.Success)?.data?.artist ?: return
		viewModelScope.launch {
			runCatching {
				if (starred) {
					artistRepository.starArtist(artist)
				} else {
					artistRepository.unstarArtist(artist)
				}
				_starred.value = starred
			}
		}
	}

	fun starAlbum(starred: Boolean) {
		viewModelScope.launch {
			val selection = _selectedAlbum.value ?: return@launch
			runCatching {
				if (starred) {
					albumRepository.starAlbum(selection)
				} else {
					albumRepository.unstarAlbum(selection)
				}
				_selectedAlbumIsStarred.value = starred
			}
		}
	}

	fun requestAurralAlbum(row: AurralMissingAlbumRow) {
		val artist = (_artistState.value as? UiState.Success)?.data?.artist ?: return
		viewModelScope.launch {
			aurralRepository.requestAlbum(artist, row.releaseGroup)
				.onSuccess {
					updateAurralAlbumRequestStatus(row, "requested")
				}
				.onFailure { error ->
					Logger.w("ArtistDetailViewModel", "Failed to request Aurral album", error)
					val latestState = (_artistState.value as? UiState.Success)?.data ?: return@onFailure
					_artistState.value = UiState.Success(
						latestState.copy(
							aurralError = error.message ?: error::class.simpleName
						)
					)
				}
		}
	}

	private fun updateAurralAlbumRequestStatus(
		row: AurralMissingAlbumRow,
		status: String
	) {
		val currentState = (_artistState.value as? UiState.Success)?.data ?: return
		val releaseGroupId = row.releaseGroup.id
		_artistState.value = UiState.Success(
			currentState.copy(
				aurralAlbumRequests = currentState.aurralAlbumRequests
					.filterNot { request -> request.albumMbid == releaseGroupId }
					.plus(
						AurralAlbumRequest(
							albumMbid = releaseGroupId,
							albumName = row.title,
							artistMbid = currentState.artist.musicBrainzId,
							artistName = currentState.artist.name,
							status = status
						)
					),
				aurralMissingAlbums = currentState.aurralMissingAlbums.map { row ->
					if (row.releaseGroup.id == releaseGroupId) {
						row.copy(
							requestStatus = status,
							requestable = false,
							acquisitionProgress = aurralAcquisitionProgress(status)
						)
					} else {
						row
					}
				}
			)
		)
	}

	fun monitorArtistInAurral() {
		setArtistMonitoringInAurral(monitored = true)
	}

	fun setArtistMonitoringInAurral(monitored: Boolean) {
		val state = (_artistState.value as? UiState.Success)?.data ?: return
		val artist = state.aurralActionArtist() ?: return
		viewModelScope.launch {
			_monitoringInAurral.value = true
			aurralRepository.setArtistMonitoring(artist, monitored)
				.onSuccess {
					val latestState = (_artistState.value as? UiState.Success)?.data
					if (latestState != null) {
						_artistState.value = UiState.Success(
							latestState.copy(
								aurralMonitored = monitored,
								aurralError = null
							)
						)
					}
				}
				.onFailure { error ->
					Logger.w("ArtistDetailViewModel", "Failed to monitor artist in Aurral", error)
					val latestState = (_artistState.value as? UiState.Success)?.data ?: return@onFailure
					_artistState.value = UiState.Success(
						latestState.copy(
							aurralError = error.message ?: error::class.simpleName
						)
					)
				}
			_monitoringInAurral.value = false
		}
	}

	fun playArtistAlbums(player: MediaPlayerViewModel) {
		(_artistState.value as? UiState.Success)?.data?.let { state ->
			player.clearQueue()
			player.setPlaybackOrigin(artistDetailPlaybackOrigin(state))
			state.albums.forEach { album ->
				player.addToQueue(album)
			}
			player.playAt(0)
		}
	}

	fun shuffleArtistAlbums(player: MediaPlayerViewModel) {
		(_artistState.value as? UiState.Success)?.data?.let { state ->
			val songs = state.albums.flatMap { it.songs }.shuffled()
			if (songs.isEmpty()) return
			player.clearQueue()
			player.setPlaybackOrigin(artistDetailPlaybackOrigin(state))
			songs.forEach { song ->
				player.addToQueueSingle(song)
			}
			player.playAt(0)
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

	@OptIn(ExperimentalCoroutinesApi::class)
	fun collectionDownloadStatus(): Flow<DownloadStatus> {
		return artistState.flatMapLatest { state ->
			if (state is UiState.Success) {
				val allArtistSongIds = state.data.albums.flatMap { album ->
					album.songs.map { it.id }
				}

				if (allArtistSongIds.isEmpty()) {
					flowOf(DownloadStatus.NOT_DOWNLOADED)
				} else {
					downloadManager.getCollectionDownloadStatus(allArtistSongIds)
				}
			} else {
				flowOf(DownloadStatus.NOT_DOWNLOADED)
			}
		}
	}
}

private fun ArtistState.aurralActionArtist(): DomainArtist? {
	val artistMbid = aurralArtistMbid?.trim()?.takeIf { it.isNotEmpty() }
		?: artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
		?: return null
	val artistName = aurralArtistName?.trim()?.takeIf { it.isNotEmpty() }
		?: artist.name.trim().takeIf { it.isNotEmpty() }
		?: artistMbid
	return artist.copy(
		name = artistName,
		musicBrainzId = artistMbid
	)
}
