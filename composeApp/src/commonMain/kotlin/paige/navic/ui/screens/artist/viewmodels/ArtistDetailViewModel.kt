package paige.navic.ui.screens.artist.viewmodels

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
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
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.AurralMissingAlbumRow
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralArtistExternalLink
import paige.navic.domain.models.AurralArtistOwnershipAlbumRow
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralSimilarArtistRow
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.models.aurralArtistOwnershipAlbumRows
import paige.navic.domain.models.aurralMissingAlbumRows
import paige.navic.domain.models.aurralSimilarArtistRows
import paige.navic.domain.models.IntegrationService
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
import paige.navic.domain.manager.PreferenceManager
import paige.navic.util.core.Logger
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.artist.artistDetailPlaybackOrigin
import paige.navic.ui.screens.artist.artistDetailCachedImageUrl
import paige.navic.ui.screens.artist.artistDetailAurralCandidateArtist
import paige.navic.ui.screens.artist.artistDetailAurralFallbackIdentities
import paige.navic.ui.screens.artist.artistDetailPhotoCacheEntity
import paige.navic.ui.screens.artist.artistLastFmTopTrackSongs
import paige.navic.ui.screens.artist.artistHeaderImageCacheIndex
import paige.navic.ui.screens.artist.shouldApplyLastFmTopTrackResult
import paige.navic.ui.screens.artist.toArtistHeaderImageCacheEntry
import paige.navic.ui.screens.artist.withCachedArtistPhoto
import paige.navic.ui.screens.aurral.aurralArtistIdentityCandidatesForLocalArtist
import paige.navic.ui.screens.aurral.aurralRecommendedAlbumsForArtist
import paige.navic.ui.screens.aurral.aurralSimilarArtistImageCandidates
import paige.navic.ui.screens.aurral.shouldLoadAurralUi
import kotlin.time.Clock

@Immutable
data class ArtistState(
	val artist: DomainArtist,
	val albums: List<DomainAlbum>,
	val topSongs: List<DomainSong>,
	val lastFmTopSongs: List<DomainSong> = emptyList(),
	val similarArtists: List<DomainArtist> = emptyList(),
	val aurralAlbumRequests: List<AurralAlbumRequest> = emptyList(),
	val aurralMissingAlbums: List<AurralMissingAlbumRow> = emptyList(),
	val aurralOwnedOrPartialAlbums: List<AurralArtistOwnershipAlbumRow> = emptyList(),
	val aurralMissingReleaseGroups: List<AurralArtistOwnershipAlbumRow> = emptyList(),
	val aurralRecommendedAlbums: List<AurralAlbumSearchItem> = emptyList(),
	val aurralSimilarArtists: List<AurralSimilarArtistRow> = emptyList(),
	val aurralPreviewTracks: List<AurralPreviewTrack> = emptyList(),
	val aurralMonitored: Boolean? = null,
	val aurralArtistMbid: String? = null,
	val aurralArtistName: String? = null,
	val aurralArtistBio: String? = null,
	val aurralArtistGenres: List<String> = emptyList(),
	val aurralArtistExternalLinks: List<AurralArtistExternalLink> = emptyList(),
	val aurralArtistImageUrl: String? = null,
	val aurralLoading: Boolean = false,
	val aurralProfileLoading: Boolean = false,
	val aurralOwnershipLoading: Boolean = false,
	val aurralPreviewTracksLoading: Boolean = false,
	val aurralSimilarArtistsLoading: Boolean = false,
	val aurralRequestsLoading: Boolean = false,
	val lastFmLoading: Boolean = false,
	val aurralError: String? = null,
	val aurralProfileError: String? = null,
	val aurralOwnershipError: String? = null,
	val aurralPreviewTracksError: String? = null,
	val aurralSimilarArtistsError: String? = null,
	val aurralRequestsError: String? = null,
	val aurralFeedback: AurralArtistActionFeedback? = null
)

enum class AurralArtistActionFeedback {
	AlbumRequested,
	MonitoringQueued,
	UnmonitoringQueued,
	MonitoringEnabled,
	MonitoringDisabled
}

class ArtistDetailViewModel(
	private val artistId: String,
	private val repository: DbRepository,
	private val artistRepository: ArtistRepository,
	private val songRepository: SongRepository,
	private val albumRepository: AlbumRepository,
	private val aurralRepository: AurralRepository,
	private val lastFmRepository: LastFmRepository,
	private val preferenceManager: PreferenceManager,
	playlistRepository: PlaylistRepository,
	private val artistDao: ArtistDao,
	private val albumDao: AlbumDao,
	private val artistPhotoCacheDao: ArtistPhotoCacheDao,
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

	private val integrationEnabledListenerRemovers = mutableListOf<() -> Unit>()

	val isOnline = connectivityManager.isOnline

	val allDownloads = downloadManager.allDownloads
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = emptyList()
		)

	val scrollState = ScrollState(initial = 0)

	init {
		integrationEnabledListenerRemovers += preferenceManager.addIntegrationEnabledChangeListener(IntegrationService.Aurral) { enabled ->
			if (!enabled) clearAurralUiState()
		}
		integrationEnabledListenerRemovers += preferenceManager.addIntegrationEnabledChangeListener(IntegrationService.LastFm) { enabled ->
			if (!enabled) clearLastFmUiState()
		}
		loadArtistData()
		viewModelScope.launch {
			aurralRepository.artistStateRevision.drop(1).collect {
				refreshAurralEnrichment()
			}
		}
	}

	override fun onCleared() {
		integrationEnabledListenerRemovers.forEach { removeListener -> removeListener() }
		integrationEnabledListenerRemovers.clear()
		super.onCleared()
	}

	private fun loadArtistData() {
		viewModelScope.launch {
			try {
				val artistEntity = artistDao.getArtistById(artistId)
				val domainArtist = artistEntity?.toDomainModel()
					?: fallbackArtistFromRouteId(artistId)

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
				val artistPhotoCacheEntries = artistPhotoCacheDao.getArtistPhotoCache()
					.map { entry -> entry.toArtistHeaderImageCacheEntry() }
				val artistPhotoCacheIndex = artistHeaderImageCacheIndex(artistPhotoCacheEntries)
				val cachedArtistImageUrl = artistDetailCachedImageUrl(
					artist = domainArtist,
					index = artistPhotoCacheIndex,
					artistArtworkPriority = preferenceManager.artistArtworkPriority,
					externalArtworkEnabled = preferenceManager.aurralEnabled
				)
				val cachedSimilarArtists = initialSimilarArtists.map { similarArtist ->
					similarArtist.withCachedArtistPhoto(
						entries = artistPhotoCacheEntries,
						artistArtworkPriority = preferenceManager.artistArtworkPriority,
						externalArtworkEnabled = preferenceManager.aurralEnabled
					)
				}

				_starred.value = artistEntity != null && artistRepository.isArtistStarred(domainArtist)

				_artistState.value = UiState.Success(
					ArtistState(
						artist = domainArtist,
						albums = domainAlbums,
						topSongs = domainSongs,
						similarArtists = cachedSimilarArtists,
						aurralArtistImageUrl = cachedArtistImageUrl
					)
				)
				loadLastFmTopTracks(domainArtist, allArtistSongs)
				loadAurralEnrichment(domainArtist, domainAlbums)

				if (artistEntity != null) {
					repository.fetchArtistMetadata(artistId)
						.onSuccess { updatedArtist ->
							val currentState = (_artistState.value as? UiState.Success)?.data
							if (currentState != null) {
								val shouldRefreshAurral =
									updatedArtist.musicBrainzId != currentState.artist.musicBrainzId

								val updatedSimilarArtists =
									updatedArtist.similarArtistIds.mapNotNull { id ->
										artistDao.getArtistById(id)?.toDomainModel()
									}.map { similarArtist ->
										similarArtist.withCachedArtistPhoto(
											entries = artistPhotoCacheEntries,
											artistArtworkPriority = preferenceManager.artistArtworkPriority,
											externalArtworkEnabled = preferenceManager.aurralEnabled
										)
									}
									val cachedUpdatedArtistImageUrl =
									currentState.aurralArtistImageUrl ?: artistDetailCachedImageUrl(
										artist = updatedArtist,
										index = artistPhotoCacheIndex,
										artistArtworkPriority = preferenceManager.artistArtworkPriority,
										externalArtworkEnabled = preferenceManager.aurralEnabled
									)

								_artistState.value = UiState.Success(
									currentState.copy(
										artist = updatedArtist,
										similarArtists = updatedSimilarArtists,
										aurralArtistImageUrl = cachedUpdatedArtistImageUrl
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
		if (!preferenceManager.lastFmEnabled || preferenceManager.lastFmApiKey.isBlank()) {
			val currentState = (_artistState.value as? UiState.Success)?.data ?: return
			if (currentState.artist.id == artist.id) {
				_artistState.value = UiState.Success(
					currentState.copy(
						lastFmTopSongs = emptyList(),
						lastFmLoading = false
					)
				)
			}
			return
		}
		viewModelScope.launch {
			val currentState = (_artistState.value as? UiState.Success)?.data ?: return@launch
			if (currentState.artist.id == artist.id) {
				_artistState.value = UiState.Success(currentState.copy(lastFmLoading = true))
			}
			lastFmRepository.getArtistTopTracks(
				artistName = artist.name,
				artistMbid = artist.musicBrainzId
			).onSuccess { tracks ->
				val latestState = (_artistState.value as? UiState.Success)?.data ?: return@onSuccess
				if (
					!shouldApplyLastFmTopTrackResult(
						lastFmEnabled = preferenceManager.lastFmEnabled,
						lastFmApiKey = preferenceManager.lastFmApiKey,
						currentArtistId = latestState.artist.id,
						resultArtistId = artist.id
					)
				) {
					if (latestState.artist.id == artist.id) {
						_artistState.value = UiState.Success(
							latestState.copy(
								lastFmTopSongs = emptyList(),
								lastFmLoading = false
							)
						)
					}
					return@onSuccess
				}
				_artistState.value = UiState.Success(
					latestState.copy(
						lastFmTopSongs = artistLastFmTopTrackSongs(
							tracks = tracks,
							localSongs = localSongs
						),
						lastFmLoading = false
					)
				)
			}.onFailure { error ->
				Logger.w("ArtistDetailViewModel", "Failed to fetch Last.fm top tracks", error)
				val latestState = (_artistState.value as? UiState.Success)?.data ?: return@onFailure
				if (latestState.artist.id == artist.id) {
					_artistState.value = UiState.Success(latestState.copy(lastFmLoading = false))
				}
			}
		}
	}

	private fun loadAurralEnrichment(
		artist: DomainArtist,
		albums: List<DomainAlbum>
	) {
		if (!canLoadAurral()) {
			clearAurralUiState()
			return
		}
		viewModelScope.launch(Dispatchers.IO) {
			val currentState = (_artistState.value as? UiState.Success)?.data ?: return@launch
			_artistState.value = UiState.Success(
				currentState.copy(
					aurralLoading = true,
					aurralProfileLoading = true,
					aurralOwnershipLoading = true,
					aurralPreviewTracksLoading = true,
					aurralSimilarArtistsLoading = true,
					aurralRequestsLoading = true,
					aurralError = null,
					aurralProfileError = null,
					aurralOwnershipError = null,
					aurralPreviewTracksError = null,
					aurralSimilarArtistsError = null,
					aurralRequestsError = null
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
			primaryAurralArtist
				?.let { aurralRepository.getCachedArtistEnrichment(it).getOrNull() }
				?.let { cachedEnrichment ->
					applyAurralEnrichmentSnapshot(
						artist = artist,
						albums = albums,
						enrichment = cachedEnrichment,
						loading = true
					)
				}
			val discoveryDeferred = async {
				aurralRepository.getDiscovery(hydrateMissingImages = false).getOrNull()
			}
			val coreEnrichmentResultsByMbid = mutableMapOf<String, Result<AurralArtistEnrichment?>>()
			suspend fun coreEnrichmentResultFor(aurralArtist: DomainArtist): Result<AurralArtistEnrichment?> {
				val cacheKey = aurralArtist.musicBrainzId.orEmpty()
					.trim()
					.takeIf { it.isNotEmpty() }
					?: aurralArtist.name.trim()
				coreEnrichmentResultsByMbid[cacheKey]?.let { return it }
				val result = aurralRepository.getArtistCoreEnrichment(aurralArtist)
				coreEnrichmentResultsByMbid[cacheKey] = result
				return result
			}
			val primaryCoreEnrichmentResult = primaryAurralArtist
				?.let { aurralArtist -> coreEnrichmentResultFor(aurralArtist) }
				?.also { result ->
					result.getOrNull()?.let { enrichment ->
						applyAurralCoreEnrichmentSnapshot(
							artist = artist,
							albums = albums,
							enrichment = enrichment,
							artistImageUrl = null
						)
					}
				}
			val discovery = discoveryDeferred.await()
			val aurralIdentities = discovery
				?.let { summary -> aurralArtistIdentityCandidatesForLocalArtist(summary, artist) }
				.orEmpty()
				.ifEmpty { artistDetailAurralFallbackIdentities(artist) }
			val aurralArtistCandidates = aurralIdentities.map { identity ->
				identity to artistDetailAurralCandidateArtist(artist, identity)
			}

			if (aurralArtistCandidates.isEmpty()) {
				val latestState = (_artistState.value as? UiState.Success)?.data ?: return@launch
				_artistState.value = UiState.Success(
					latestState.copy(
						aurralLoading = false,
						aurralProfileLoading = false,
						aurralOwnershipLoading = false,
						aurralPreviewTracksLoading = false,
						aurralSimilarArtistsLoading = false,
						aurralRequestsLoading = false,
						aurralError = null
					)
				)
				return@launch
			}

			var selectedCandidate = aurralArtistCandidates.first()
			var aurralArtist = selectedCandidate.second
			var coreEnrichmentResult = primaryCoreEnrichmentResult
				?.takeIf { primaryAurralArtist.musicBrainzId == aurralArtist.musicBrainzId }
				?: coreEnrichmentResultFor(aurralArtist)
			if (coreEnrichmentResult.isFailure || coreEnrichmentResult.getOrNull()?.monitored == null) {
				for (candidate in aurralArtistCandidates.drop(1)) {
					val candidateArtist = candidate.second
					val candidateResult = coreEnrichmentResultFor(candidateArtist)
					val currentMonitoring = coreEnrichmentResult.getOrNull()?.monitored
					val candidateMonitoring = candidateResult.getOrNull()?.monitored
					val shouldUseCandidate = when {
						candidateResult.isFailure -> false
						coreEnrichmentResult.isFailure -> true
						currentMonitoring == null && candidateMonitoring != null -> true
						coreEnrichmentResult.getOrNull() == null && candidateResult.getOrNull() != null -> true
						else -> false
					}
					if (shouldUseCandidate) {
						selectedCandidate = candidate
						aurralArtist = candidateArtist
						coreEnrichmentResult = candidateResult
					}
					if (coreEnrichmentResult.isSuccess && coreEnrichmentResult.getOrNull()?.monitored != null) {
						break
					}
				}
			}
			val verifiedAurralArtistImageUrl = selectedCandidate.first.imageUrl
				?.trim()
				?.takeIf { it.isNotEmpty() }
				?: aurralIdentities.firstNotNullOfOrNull { identity ->
					identity.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
				}
			coreEnrichmentResult
				.getOrNull()
				?.let { enrichment ->
					applyAurralCoreEnrichmentSnapshot(
						artist = artist,
						albums = albums,
						enrichment = enrichment,
						artistImageUrl = verifiedAurralArtistImageUrl
					)
				}

			val coreEnrichment = coreEnrichmentResult.getOrNull()
			if (coreEnrichment == null) {
				val error = coreEnrichmentResult.exceptionOrNull()
					?: IllegalStateException("Aurral artist profile failed")
				Logger.w("ArtistDetailViewModel", "Failed to fetch Aurral artist profile", error)
				val latestState = (_artistState.value as? UiState.Success)?.data ?: return@launch
				_artistState.value = UiState.Success(
					latestState.copy(
						aurralLoading = false,
						aurralProfileLoading = false,
						aurralOwnershipLoading = false,
						aurralPreviewTracksLoading = false,
						aurralSimilarArtistsLoading = false,
						aurralRequestsLoading = false,
						aurralError = error.message ?: error::class.simpleName,
						aurralProfileError = error.message ?: error::class.simpleName,
						aurralOwnershipError = error.message ?: error::class.simpleName,
						aurralPreviewTracksError = error.message ?: error::class.simpleName,
						aurralSimilarArtistsError = error.message ?: error::class.simpleName,
						aurralRequestsError = error.message ?: error::class.simpleName
					)
				)
				return@launch
			}

			val resolvedAurralArtistMbid = coreEnrichment.artistMbid
				.trim()
				.takeIf { it.isNotEmpty() }
				?: aurralArtist.musicBrainzId
			val resolvedAurralArtistName = coreEnrichment.artistName
				.trim()
				.takeIf { it.isNotEmpty() }
				?: aurralArtist.name
			val resolvedAurralArtist = aurralArtist.copy(
				name = resolvedAurralArtistName,
				musicBrainzId = resolvedAurralArtistMbid
			)
			val artistPhotoCacheEntries = artistPhotoCacheDao.getArtistPhotoCache()
				.map { entry -> entry.toArtistHeaderImageCacheEntry() }
			val localArtists = artistDao.getAllArtistsList().map { artist ->
				artist.toDomainModel().withCachedArtistPhoto(
					entries = artistPhotoCacheEntries,
					artistArtworkPriority = preferenceManager.artistArtworkPriority,
					externalArtworkEnabled = preferenceManager.aurralEnabled
				)
			}
			val recommendedAlbums = discovery
				?.let { discovery ->
					aurralRecommendedAlbumsForArtist(
						discovery = discovery,
						artistMbid = resolvedAurralArtistMbid,
						artistName = resolvedAurralArtistName
					)
				}
				.orEmpty()
			val externalArtistImageCandidates = discovery
				?.let { summary ->
					aurralSimilarArtistImageCandidates(
						discovery = summary,
						artistPhotoCacheEntries = artistPhotoCacheEntries,
						artistArtworkPriority = preferenceManager.artistArtworkPriority,
						externalArtworkEnabled = preferenceManager.aurralEnabled
					)
				}
				.orEmpty()
			val latestState = (_artistState.value as? UiState.Success)?.data ?: return@launch
			persistArtistPhotoCache(
				localArtist = latestState.artist,
				sourceArtist = resolvedAurralArtist,
				imageUrl = verifiedAurralArtistImageUrl
			)
			_artistState.value = UiState.Success(
				latestState.copy(
					aurralRecommendedAlbums = recommendedAlbums,
					aurralArtistImageUrl = verifiedAurralArtistImageUrl
						?: latestState.aurralArtistImageUrl,
					aurralLoading = false,
					aurralProfileLoading = false,
					aurralOwnershipLoading = false,
					aurralPreviewTracksLoading = latestState.aurralPreviewTracks.isEmpty(),
					aurralSimilarArtistsLoading = latestState.aurralSimilarArtists.isEmpty(),
					aurralRequestsLoading = latestState.aurralAlbumRequests.isEmpty(),
					aurralProfileError = null,
					aurralOwnershipError = null,
					aurralPreviewTracksError = null,
					aurralSimilarArtistsError = null,
					aurralRequestsError = null,
					aurralError = null
				)
			)
			(_artistState.value as? UiState.Success)?.data?.let { stateAfterCore ->
				launch {
					hydrateAurralArtistAlbumCovers(
						stateArtistId = stateAfterCore.artist.id,
						artist = resolvedAurralArtist,
						ownedOrPartialRows = stateAfterCore.aurralOwnedOrPartialAlbums,
						missingReleaseGroupRows = stateAfterCore.aurralMissingReleaseGroups,
						missingAlbumRows = stateAfterCore.aurralMissingAlbums
					)
				}
			}

			launch {
				aurralRepository.getArtistAlbumRequests(aurralArtist)
					.onSuccess { requests ->
						val enrichment = coreEnrichment.copy(requests = requests)
						applyAurralAlbumRequestSnapshot(
							artist = artist,
							albums = albums,
							enrichment = enrichment
						)
						val latest = (_artistState.value as? UiState.Success)?.data ?: return@onSuccess
						hydrateAurralArtistAlbumCovers(
							stateArtistId = latest.artist.id,
							artist = resolvedAurralArtist,
							ownedOrPartialRows = latest.aurralOwnedOrPartialAlbums,
							missingReleaseGroupRows = latest.aurralMissingReleaseGroups,
							missingAlbumRows = latest.aurralMissingAlbums
						)
					}
					.onFailure { error ->
						Logger.w("ArtistDetailViewModel", "Failed to refresh Aurral album requests", error)
						markAurralAlbumRequestsRefreshFailed(artist, error)
					}
			}
			launch {
				aurralRepository.getArtistPreviewTracks(aurralArtist)
					.onSuccess { tracks ->
						applyAurralPreviewTracksSnapshot(
							artist = artist,
							tracks = tracks
						)
					}
					.onFailure { error ->
						Logger.w("ArtistDetailViewModel", "Failed to refresh Aurral preview tracks", error)
						markAurralPreviewTracksRefreshFailed(artist, error)
					}
			}
			launch {
				aurralRepository.getArtistSimilarArtists(aurralArtist)
					.onSuccess { similarArtists ->
						val enrichment = coreEnrichment.copy(similarArtists = similarArtists)
						val latest = (_artistState.value as? UiState.Success)?.data ?: return@onSuccess
						applyAurralSimilarArtistRowsSnapshot(
							artist = artist,
							rows = aurralSimilarArtistRows(
								enrichment = enrichment,
								allLocalArtists = localArtists,
								localSimilarArtists = latest.similarArtists,
								externalArtists = externalArtistImageCandidates
							)
						)
					}
					.onFailure { error ->
						Logger.w("ArtistDetailViewModel", "Failed to refresh Aurral similar artists", error)
						markAurralSimilarArtistsRefreshFailed(artist, error)
					}
			}
		}
	}

	private fun applyAurralEnrichmentSnapshot(
		artist: DomainArtist,
		albums: List<DomainAlbum>,
		enrichment: AurralArtistEnrichment,
		loading: Boolean,
		artistImageUrl: String? = null
	) {
		val latestState = (_artistState.value as? UiState.Success)?.data ?: return
		if (latestState.artist.id != artist.id) return
		val ownershipRows = aurralArtistOwnershipAlbumRows(enrichment, albums)
		val missingAlbumRows = aurralMissingAlbumRows(enrichment, albums)
		val hasOwnershipRows = ownershipRows.ownedOrPartial.isNotEmpty() || ownershipRows.missing.isNotEmpty()
		val nextOwnedOrPartialRows = if (loading && !hasOwnershipRows) {
			latestState.aurralOwnedOrPartialAlbums
		} else {
			ownershipRows.ownedOrPartial
		}
		val nextMissingReleaseGroups = if (loading && !hasOwnershipRows) {
			latestState.aurralMissingReleaseGroups
		} else {
			ownershipRows.missing
		}
		val nextMissingAlbums = if (loading && missingAlbumRows.isEmpty()) {
			latestState.aurralMissingAlbums
		} else {
			missingAlbumRows
		}
		val nextSimilarArtists = if (loading && enrichment.similarArtists.isEmpty()) {
			latestState.aurralSimilarArtists
		} else {
			aurralSimilarArtistRows(
				enrichment = enrichment,
				allLocalArtists = emptyList(),
				localSimilarArtists = latestState.similarArtists
			)
		}
		val nextPreviewTracks = if (loading && enrichment.previewTracks.isEmpty()) {
			latestState.aurralPreviewTracks
		} else {
			enrichment.previewTracks
		}
		val nextRequests = if (loading && enrichment.requests.isEmpty()) {
			latestState.aurralAlbumRequests
		} else {
			enrichment.requests
		}
		_artistState.value = UiState.Success(
			latestState.copy(
				aurralMissingAlbums = nextMissingAlbums,
				aurralOwnedOrPartialAlbums = nextOwnedOrPartialRows,
				aurralMissingReleaseGroups = nextMissingReleaseGroups,
				aurralAlbumRequests = nextRequests,
				aurralSimilarArtists = nextSimilarArtists,
				aurralPreviewTracks = nextPreviewTracks,
				aurralMonitored = enrichment.monitored ?: latestState.aurralMonitored,
				aurralArtistMbid = enrichment.artistMbid,
				aurralArtistName = enrichment.artistName,
				aurralArtistBio = enrichment.bio?.trim()?.takeIf { it.isNotEmpty() },
				aurralArtistGenres = enrichment.genres,
				aurralArtistExternalLinks = enrichment.externalLinks,
				aurralArtistImageUrl = artistImageUrl
					?.trim()
					?.takeIf { it.isNotEmpty() }
					?: latestState.aurralArtistImageUrl,
				aurralLoading = loading,
				aurralProfileLoading = loading && enrichment.artistName.isBlank() &&
					enrichment.bio.isNullOrBlank() &&
					enrichment.genres.isEmpty() &&
					enrichment.externalLinks.isEmpty(),
				aurralOwnershipLoading = loading && !hasOwnershipRows,
				aurralPreviewTracksLoading = loading && enrichment.previewTracks.isEmpty(),
				aurralSimilarArtistsLoading = loading && enrichment.similarArtists.isEmpty(),
				aurralRequestsLoading = loading && enrichment.requests.isEmpty(),
				aurralProfileError = null,
				aurralOwnershipError = null,
				aurralPreviewTracksError = null,
				aurralSimilarArtistsError = null,
				aurralRequestsError = null,
				aurralError = null
			)
		)
	}

	private fun applyAurralCoreEnrichmentSnapshot(
		artist: DomainArtist,
		albums: List<DomainAlbum>,
		enrichment: AurralArtistEnrichment,
		artistImageUrl: String?
	) {
		applyAurralEnrichmentSnapshot(
			artist = artist,
			albums = albums,
			enrichment = enrichment,
			loading = true,
			artistImageUrl = artistImageUrl
		)
	}

	private fun applyAurralAlbumRequestSnapshot(
		artist: DomainArtist,
		albums: List<DomainAlbum>,
		enrichment: AurralArtistEnrichment
	) {
		val latestState = (_artistState.value as? UiState.Success)?.data ?: return
		if (latestState.artist.id != artist.id) return
		val ownershipRows = aurralArtistOwnershipAlbumRows(enrichment, albums)
		val missingAlbumRows = aurralMissingAlbumRows(enrichment, albums)
		_artistState.value = UiState.Success(
			latestState.copy(
				aurralAlbumRequests = enrichment.requests,
				aurralOwnedOrPartialAlbums = ownershipRows.ownedOrPartial,
				aurralMissingReleaseGroups = ownershipRows.missing,
				aurralMissingAlbums = missingAlbumRows,
				aurralRequestsLoading = false,
				aurralOwnershipLoading = false,
				aurralRequestsError = null,
				aurralOwnershipError = null,
				aurralError = null
			)
		)
	}

	private fun applyAurralPreviewTracksSnapshot(
		artist: DomainArtist,
		tracks: List<AurralPreviewTrack>
	) {
		val latestState = (_artistState.value as? UiState.Success)?.data ?: return
		if (latestState.artist.id != artist.id) return
		_artistState.value = UiState.Success(
			latestState.copy(
				aurralPreviewTracks = tracks,
				aurralPreviewTracksLoading = false,
				aurralPreviewTracksError = null,
				aurralError = null
			)
		)
	}

	private fun applyAurralSimilarArtistRowsSnapshot(
		artist: DomainArtist,
		rows: List<AurralSimilarArtistRow>
	) {
		val latestState = (_artistState.value as? UiState.Success)?.data ?: return
		if (latestState.artist.id != artist.id) return
		_artistState.value = UiState.Success(
			latestState.copy(
				aurralSimilarArtists = rows,
				aurralSimilarArtistsLoading = false,
				aurralSimilarArtistsError = null,
				aurralError = null
			)
		)
	}

	private fun markAurralAlbumRequestsRefreshFailed(
		artist: DomainArtist,
		error: Throwable
	) {
		val latestState = (_artistState.value as? UiState.Success)?.data ?: return
		if (latestState.artist.id != artist.id) return
		val message = error.message ?: error::class.simpleName
		_artistState.value = UiState.Success(
			latestState.copy(
				aurralRequestsLoading = false,
				aurralOwnershipLoading = false,
				aurralRequestsError = message,
				aurralOwnershipError = message
			)
		)
	}

	private fun markAurralPreviewTracksRefreshFailed(
		artist: DomainArtist,
		error: Throwable
	) {
		val latestState = (_artistState.value as? UiState.Success)?.data ?: return
		if (latestState.artist.id != artist.id) return
		_artistState.value = UiState.Success(
			latestState.copy(
				aurralPreviewTracksLoading = false,
				aurralPreviewTracksError = error.message ?: error::class.simpleName
			)
		)
	}

	private fun markAurralSimilarArtistsRefreshFailed(
		artist: DomainArtist,
		error: Throwable
	) {
		val latestState = (_artistState.value as? UiState.Success)?.data ?: return
		if (latestState.artist.id != artist.id) return
		_artistState.value = UiState.Success(
			latestState.copy(
				aurralSimilarArtistsLoading = false,
				aurralSimilarArtistsError = error.message ?: error::class.simpleName
			)
		)
	}

	private suspend fun hydrateAurralArtistAlbumCovers(
		stateArtistId: String,
		artist: DomainArtist,
		ownedOrPartialRows: List<AurralArtistOwnershipAlbumRow>,
		missingReleaseGroupRows: List<AurralArtistOwnershipAlbumRow>,
		missingAlbumRows: List<AurralMissingAlbumRow>
	) {
		val hydratedOwnedOrPartialRows = resolveAurralOwnershipAlbumCovers(
			artist = artist,
			rows = ownedOrPartialRows
		)
		val hydratedMissingReleaseGroupRows = resolveAurralOwnershipAlbumCovers(
			artist = artist,
			rows = missingReleaseGroupRows
		)
		val hydratedMissingAlbumRows = resolveAurralMissingAlbumCovers(
			artist = artist,
			rows = missingAlbumRows
		)
		val latestState = (_artistState.value as? UiState.Success)?.data ?: return
		if (latestState.artist.id != stateArtistId) return
		_artistState.value = UiState.Success(
			latestState.copy(
				aurralOwnedOrPartialAlbums = latestState.aurralOwnedOrPartialAlbums
					.withHydratedOwnershipCovers(hydratedOwnedOrPartialRows),
				aurralMissingReleaseGroups = latestState.aurralMissingReleaseGroups
					.withHydratedOwnershipCovers(hydratedMissingReleaseGroupRows),
				aurralMissingAlbums = latestState.aurralMissingAlbums
					.withHydratedMissingAlbumCovers(hydratedMissingAlbumRows)
			)
		)
	}

	private suspend fun persistArtistPhotoCache(
		localArtist: DomainArtist,
		sourceArtist: DomainArtist,
		imageUrl: String?
	) {
		val resolvedImageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return
		val cacheEntry = artistDetailPhotoCacheEntity(
			localArtist = localArtist,
			sourceArtist = sourceArtist,
			imageUrl = resolvedImageUrl,
			nowMillis = Clock.System.now().toEpochMilliseconds()
		) ?: return
		artistPhotoCacheDao.upsertArtistPhotoCacheEntries(listOf(cacheEntry))
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

	private suspend fun resolveAurralOwnershipAlbumCovers(
		artist: DomainArtist,
		rows: List<AurralArtistOwnershipAlbumRow>
	): List<AurralArtistOwnershipAlbumRow> = coroutineScope {
		rows.map { row ->
			async {
				val releaseGroup = row.releaseGroup
				if (!row.coverUrl.isNullOrBlank() || releaseGroup == null) {
					row
				} else {
					aurralRepository.getReleaseGroupCoverImageUrl(releaseGroup, artist.name)
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
				loadArtistData()
			}
		}
	}

	fun unstarSelectedSong() {
		viewModelScope.launch {
			val selection = _selectedSong.value ?: return@launch
			runCatching {
				_selectedSongIsStarred.value = false
				songRepository.unstarSong(selection)
				loadArtistData()
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
		if (!canLoadAurral()) {
			clearAurralUiState()
			return
		}
		val artist = (_artistState.value as? UiState.Success)?.data?.artist ?: return
		updateAurralAlbumRequestStatus(row, "requested", AurralArtistActionFeedback.AlbumRequested)
		viewModelScope.launch(Dispatchers.IO) {
			aurralRepository.requestAlbum(artist, row.releaseGroup)
				.onFailure { error ->
					Logger.w("ArtistDetailViewModel", "Failed to request Aurral album", error)
					updateAurralAlbumRequestStatus(
						row = row,
						status = "failed",
						errorMessage = error.message ?: error::class.simpleName
					)
				}
		}
	}

	private fun updateAurralAlbumRequestStatus(
		row: AurralMissingAlbumRow,
		status: String,
		feedback: AurralArtistActionFeedback? = null,
		errorMessage: String? = null
	) {
		val currentState = (_artistState.value as? UiState.Success)?.data ?: return
		val releaseGroupId = row.releaseGroup.id
		val requestable = status.equals("failed", ignoreCase = true)
		_artistState.value = UiState.Success(
			currentState.copy(
				aurralError = errorMessage,
				aurralFeedback = feedback ?: currentState.aurralFeedback,
				aurralAlbumRequests = currentState.aurralAlbumRequests
					.filterNot { request -> request.albumMbid == releaseGroupId }
					.plus(
						AurralAlbumRequest(
							albumMbid = releaseGroupId,
							albumName = row.title,
							artistMbid = currentState.aurralArtistMbid ?: currentState.artist.musicBrainzId,
							artistName = currentState.aurralArtistName ?: currentState.artist.name,
							status = status
						)
					),
				aurralRequestsLoading = false,
				aurralRequestsError = errorMessage,
				aurralMissingAlbums = currentState.aurralMissingAlbums.map { row ->
					if (row.releaseGroup.id == releaseGroupId) {
						row.copy(
							requestStatus = status,
							requestable = requestable,
							acquisitionProgress = aurralAcquisitionProgress(status)
						)
					} else {
						row
					}
				},
				aurralMissingReleaseGroups = currentState.aurralMissingReleaseGroups.map { row ->
					if (row.releaseGroup?.id == releaseGroupId) {
						row.copy(
							requestStatus = status,
							requestable = requestable,
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
		if (!canLoadAurral()) {
			clearAurralUiState()
			return
		}
		val state = (_artistState.value as? UiState.Success)?.data ?: return
		val artist = state.aurralActionArtist() ?: return
		viewModelScope.launch {
			_monitoringInAurral.value = true
			_artistState.value = UiState.Success(
				state.copy(
					aurralError = null,
					aurralFeedback = if (monitored) {
						AurralArtistActionFeedback.MonitoringQueued
					} else {
						AurralArtistActionFeedback.UnmonitoringQueued
					}
				)
			)
			aurralRepository.setArtistMonitoring(artist, monitored)
				.onSuccess {
					val latestState = (_artistState.value as? UiState.Success)?.data
					if (latestState != null) {
						_artistState.value = UiState.Success(
							latestState.copy(
								aurralMonitored = monitored,
								aurralError = null,
								aurralFeedback = if (monitored) {
									AurralArtistActionFeedback.MonitoringEnabled
								} else {
									AurralArtistActionFeedback.MonitoringDisabled
								}
							)
						)
					}
				}
				.onFailure { error ->
					Logger.w("ArtistDetailViewModel", "Failed to monitor artist in Aurral", error)
					val latestState = (_artistState.value as? UiState.Success)?.data ?: return@onFailure
					_artistState.value = UiState.Success(
						latestState.copy(
							aurralError = error.message ?: error::class.simpleName,
							aurralFeedback = null
						)
					)
				}
			_monitoringInAurral.value = false
		}
	}

	fun clearAurralError() {
		val state = (_artistState.value as? UiState.Success)?.data ?: return
		_artistState.value = UiState.Success(
			state.copy(
				aurralError = null,
				aurralProfileError = null,
				aurralOwnershipError = null,
				aurralPreviewTracksError = null,
				aurralSimilarArtistsError = null,
				aurralRequestsError = null
			)
		)
	}

	fun clearAurralFeedback() {
		val state = (_artistState.value as? UiState.Success)?.data ?: return
		_artistState.value = UiState.Success(state.copy(aurralFeedback = null))
	}

	private fun canLoadAurral(): Boolean =
		shouldLoadAurralUi(
			aurralEnabled = preferenceManager.aurralEnabled,
			baseUrl = preferenceManager.aurralBaseUrl
		)

	private fun clearLastFmUiState() {
		val currentState = (_artistState.value as? UiState.Success)?.data ?: return
		_artistState.value = UiState.Success(
			currentState.copy(
				lastFmTopSongs = emptyList(),
				lastFmLoading = false
			)
		)
	}

	private fun clearAurralUiState() {
		val currentState = (_artistState.value as? UiState.Success)?.data ?: return
		_monitoringInAurral.value = false
		_artistState.value = UiState.Success(
			currentState.copy(
				aurralAlbumRequests = emptyList(),
				aurralMissingAlbums = emptyList(),
				aurralOwnedOrPartialAlbums = emptyList(),
				aurralMissingReleaseGroups = emptyList(),
				aurralRecommendedAlbums = emptyList(),
				aurralSimilarArtists = emptyList(),
				aurralPreviewTracks = emptyList(),
				aurralMonitored = null,
				aurralArtistMbid = null,
				aurralArtistName = null,
				aurralArtistBio = null,
				aurralArtistGenres = emptyList(),
				aurralArtistExternalLinks = emptyList(),
				aurralArtistImageUrl = null,
				aurralLoading = false,
				aurralProfileLoading = false,
				aurralOwnershipLoading = false,
				aurralPreviewTracksLoading = false,
				aurralSimilarArtistsLoading = false,
				aurralRequestsLoading = false,
				aurralError = null,
				aurralProfileError = null,
				aurralOwnershipError = null,
				aurralPreviewTracksError = null,
				aurralSimilarArtistsError = null,
				aurralRequestsError = null,
				aurralFeedback = null
			)
		)
	}

	fun playArtistAlbums(player: MediaPlayerViewModel) {
		(_artistState.value as? UiState.Success)?.data?.let { state ->
			player.clearQueue()
			player.setPlaybackOrigin(
				artistDetailPlaybackOrigin(
					state = state,
					artistArtworkPriority = preferenceManager.artistArtworkPriority,
					externalArtworkEnabled = preferenceManager.aurralEnabled
				)
			)
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
			player.setPlaybackOrigin(
				artistDetailPlaybackOrigin(
					state = state,
					artistArtworkPriority = preferenceManager.artistArtworkPriority,
					externalArtworkEnabled = preferenceManager.aurralEnabled
				)
			)
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

private fun List<AurralArtistOwnershipAlbumRow>.withHydratedOwnershipCovers(
	hydratedRows: List<AurralArtistOwnershipAlbumRow>
): List<AurralArtistOwnershipAlbumRow> {
	val coverUrlsByReleaseGroupId = hydratedRows
		.mapNotNull { row ->
			val releaseGroupId = row.releaseGroup?.id?.trim()?.takeIf { it.isNotEmpty() }
			val coverUrl = row.coverUrl?.trim()?.takeIf { it.isNotEmpty() }
			if (releaseGroupId != null && coverUrl != null) releaseGroupId to coverUrl else null
		}
		.toMap()
	return map { row ->
		val releaseGroupId = row.releaseGroup?.id?.trim()?.takeIf { it.isNotEmpty() }
		val hydratedCoverUrl = releaseGroupId?.let(coverUrlsByReleaseGroupId::get)
		if (row.coverUrl.isNullOrBlank() && hydratedCoverUrl != null) {
			row.copy(coverUrl = hydratedCoverUrl)
		} else {
			row
		}
	}
}

private fun List<AurralMissingAlbumRow>.withHydratedMissingAlbumCovers(
	hydratedRows: List<AurralMissingAlbumRow>
): List<AurralMissingAlbumRow> {
	val coverUrlsByReleaseGroupId = hydratedRows
		.mapNotNull { row ->
			val releaseGroupId = row.releaseGroup.id.trim().takeIf { it.isNotEmpty() }
			val coverUrl = row.coverUrl?.trim()?.takeIf { it.isNotEmpty() }
			if (releaseGroupId != null && coverUrl != null) releaseGroupId to coverUrl else null
		}
		.toMap()
	return map { row ->
		val releaseGroupId = row.releaseGroup.id.trim().takeIf { it.isNotEmpty() }
		val hydratedCoverUrl = releaseGroupId?.let(coverUrlsByReleaseGroupId::get)
		if (row.coverUrl.isNullOrBlank() && hydratedCoverUrl != null) {
			row.copy(coverUrl = hydratedCoverUrl)
		} else {
			row
		}
	}
}

private fun fallbackArtistFromRouteId(artistId: String): DomainArtist {
	val fallbackName = artistId
		.trim()
		.removePrefix("name:")
		.replace(Regex("""%20""", RegexOption.IGNORE_CASE), " ")
		.replace(Regex("""[-_]+"""), " ")
		.replace(Regex("""\s+"""), " ")
		.trim()
		.takeIf { it.isNotEmpty() }
		?: artistId
	return DomainArtist(
		id = artistId,
		name = fallbackName
	)
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
