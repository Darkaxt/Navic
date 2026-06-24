package paige.navic.ui.screens.artist.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.ArtistCreditContext
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainArtistListType
import paige.navic.domain.models.artistCreditDisplayNames
import paige.navic.domain.models.artistCreditIdentityKey
import paige.navic.domain.models.splitArtistCredit
import paige.navic.domain.repositories.ArtistCreditResolutionRepository
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.ArtistRepository
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.screens.artist.artistListAurralPhotoCacheEntity
import paige.navic.ui.screens.artist.artistListAurralPhotoCandidate
import paige.navic.ui.screens.artist.artistListAurralPhotoHydrationTargets
import paige.navic.ui.screens.artist.toArtistHeaderImageCacheEntry
import paige.navic.ui.screens.artist.withCachedArtistPhoto
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.artist.aurralNameLookupArtistId
import paige.navic.util.core.Logger
import kotlin.time.Clock

class ArtistListViewModel(
	initialListType: DomainArtistListType = DomainArtistListType.AlphabeticalByName,
	private val repository: ArtistRepository,
	private val albumDao: AlbumDao,
	private val sessionManager: SessionManager,
	private val artistPhotoCacheDao: ArtistPhotoCacheDao,
	private val preferenceManager: PreferenceManager,
	private val aurralRepository: AurralRepository,
	private val artistCreditResolutionRepository: ArtistCreditResolutionRepository
) : ViewModel() {
	private val _artistsState =
		MutableStateFlow<UiState<ImmutableList<DomainArtist>>>(UiState.Loading())
	val artistsState = _artistsState.asStateFlow()

	private val _starred = MutableStateFlow(false)
	val starred = _starred.asStateFlow()

	private val _selectedArtist = MutableStateFlow<DomainArtist?>(null)
	val selectedArtist = _selectedArtist.asStateFlow()

	private val _selectedArtistAlbums = MutableStateFlow<ImmutableList<DomainAlbum>?>(null)
	val selectedArtistAlbums = _selectedArtistAlbums.asStateFlow()

	private val _listType = MutableStateFlow(initialListType)
	val listType = _listType.asStateFlow()

	private val _selectedReversed = MutableStateFlow(false)
	val selectedReversed = _selectedReversed.asStateFlow()

	val gridState = LazyGridState()
	private val attemptedAurralArtistPhotoKeys = mutableSetOf<String>()
	private val attemptedArtistCreditKeys = mutableSetOf<String>()
	private var lastRawArtists: List<DomainArtist> = emptyList()
	private var lastRawArtistKey: String = ""

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshArtists(false) }
		}
	}

	private var refreshArtistsJob: Job? = null

	fun refreshArtists(fullRefresh: Boolean) {
		refreshArtistsJob?.cancel()
		refreshArtistsJob = viewModelScope.launch {
			if (fullRefresh) {
				attemptedArtistCreditKeys.clear()
			}
			repository.getArtistsFlow(fullRefresh, _listType.value, _selectedReversed.value)
				.combine(artistPhotoCacheDao.observeArtistPhotoCache()) { state, cachedPhotos ->
					val entries = cachedPhotos.map { entry -> entry.toArtistHeaderImageCacheEntry() }
					when (state) {
						is UiState.Loading -> UiState.Loading(
							state.data?.withCachedArtistPhotos(entries)?.toImmutableList()
						)

						is UiState.Success -> UiState.Success(
							state.data.withCachedArtistPhotos(entries).toImmutableList()
						)

						is UiState.Error -> UiState.Error(
							error = state.error,
							data = state.data?.withCachedArtistPhotos(entries)?.toImmutableList()
						)
					}
				}
				.flowOn(Dispatchers.Default)
				.collect {
					_artistsState.value = it
					val rawArtists = it.data.orEmpty()
					lastRawArtists = rawArtists
					lastRawArtistKey = rawArtists.artistCreditListKey()
					hydrateAurralArtistPhotos(rawArtists)
					hydrateArtistCreditResolutions(rawArtists)
				}
		}
	}

	fun selectArtist(artist: DomainArtist) {
		viewModelScope.launch {
			_selectedArtist.value = artist
			val artistAlbums = 
				albumDao.getAlbumsByArtist(artist.id).firstOrNull() ?: emptyList()
			_selectedArtistAlbums.value = artistAlbums.map { it.toDomainModel() }.toImmutableList()
			_starred.value = repository.isArtistStarred(artist)
		}
	}

	fun clearSelection() {
		_selectedArtist.value = null
	}

	fun starArtist(starred: Boolean) {
		val artist = _selectedArtist.value ?: return
		viewModelScope.launch {
			runCatching {
				if (starred) {
					repository.starArtist(artist)
				} else {
					repository.unstarArtist(artist)
				}
				_starred.value = starred
			}
		}
	}

	fun addArtistAlbumsToQueue(player: MediaPlayerViewModel) {
		val artist = _selectedArtist.value ?: return
		viewModelScope.launch {
			val artistAlbums = 
				albumDao.getAlbumsByArtist(artist.id).firstOrNull() ?: emptyList()
			artistAlbums.map { it.toDomainModel() }.forEach { album ->
				player.addToQueue(album)
			}
		}
	}

	fun playArtistAlbumsNext(player: MediaPlayerViewModel) {
		val artist = _selectedArtist.value ?: return
		viewModelScope.launch {
			val artistAlbums = 
				albumDao.getAlbumsByArtist(artist.id).firstOrNull() ?: emptyList()
			artistAlbums.map { it.toDomainModel() }.forEach { album ->
				player.playNext(album)
			}
		}
	}

	fun setListType(listType: DomainArtistListType) {
		_listType.value = listType
		refreshArtists(false)
	}

	fun setReversed(reversed: Boolean) {
		_selectedReversed.value = reversed
		refreshArtists(false)
	}

	fun clearError() {
		_artistsState.value = UiState.Success(_artistsState.value.data ?: persistentListOf())
	}

	private fun List<DomainArtist>.withCachedArtistPhotos(
		entries: List<paige.navic.ui.screens.artist.ArtistHeaderImageCacheEntry>
	): List<DomainArtist> =
		map { artist ->
			artist.withCachedArtistPhoto(
				entries = entries,
				artistArtworkPriority = preferenceManager.artistArtworkPriority,
				externalArtworkEnabled = preferenceManager.aurralEnabled
			)
		}

	private fun hydrateAurralArtistPhotos(artists: List<DomainArtist>) {
		val targets = artistListAurralPhotoHydrationTargets(
			artists = artists,
			attemptedLookupKeys = attemptedAurralArtistPhotoKeys,
			externalArtworkEnabled = preferenceManager.aurralEnabled
		)
		if (targets.isEmpty()) return
		attemptedAurralArtistPhotoKeys += targets.map { target -> target.lookupKey }

		viewModelScope.launch(Dispatchers.IO) {
			val cacheEntries = targets.mapNotNull { target ->
				val result = aurralRepository.searchArtists(
					query = target.artist.name,
					limit = AURRAL_ARTIST_PHOTO_SEARCH_LIMIT
				)
				result.onFailure { error ->
					Logger.w(
						ARTIST_LIST_AURRAL_ARTWORK_TAG,
						"Aurral artist photo lookup failed for ${target.artist.name}",
						error
					)
				}
				val candidate = artistListAurralPhotoCandidate(
					localArtist = target.artist,
					candidates = result.getOrNull()?.artists.orEmpty()
				)
				artistListAurralPhotoCacheEntity(
					localArtist = target.artist,
					sourceArtist = candidate,
					nowMillis = Clock.System.now().toEpochMilliseconds()
				)
			}
			if (cacheEntries.isNotEmpty()) {
				artistPhotoCacheDao.upsertArtistPhotoCacheEntries(cacheEntries)
				Logger.i(
					ARTIST_LIST_AURRAL_ARTWORK_TAG,
					"Persisted ${cacheEntries.size} Aurral artist photos from artist list hydration."
				)
			}
		}
	}

	private fun hydrateArtistCreditResolutions(artists: List<DomainArtist>) {
		if (!preferenceManager.aurralEnabled) return
		val sourceKey = artists.artistCreditListKey()
		val targets = artists.mapNotNull { artist ->
			val context = artist.toArtistCreditContext()
			if (splitArtistCredit(context.originalCredit).size <= 1) return@mapNotNull null
			context.takeIf { attemptedArtistCreditKeys.add(it.artistCreditAttemptKey()) }
		}
		if (targets.isEmpty()) return

		viewModelScope.launch(Dispatchers.IO) {
			var resolvedAny = false
			targets.forEach { context ->
				val resolution = artistCreditResolutionRepository.cachedResolution(context)
					?: artistCreditResolutionRepository.resolveAndCache(context)
				if (resolution != null) {
					resolvedAny = true
				}
			}
			if (!resolvedAny || lastRawArtistKey != sourceKey) return@launch

			val expandedArtists = lastRawArtists.withCachedArtistCreditRows()
			_artistsState.value = _artistsState.value.withArtistData(expandedArtists.toImmutableList())
			hydrateAurralArtistPhotos(expandedArtists)
		}
	}

	private suspend fun List<DomainArtist>.withCachedArtistCreditRows(): List<DomainArtist> {
		val localArtistsByName = associateBy { artist -> artistCreditIdentityKey(artist.name) }
		val expanded = mutableListOf<DomainArtist>()
		val seen = mutableSetOf<String>()
		forEach { artist ->
			val context = artist.toArtistCreditContext()
			val resolution = artistCreditResolutionRepository.cachedResolution(context)
			val displayNames = artistCreditDisplayNames(context, resolution)
			val artistsForCredit = if (resolution != null && displayNames.size > 1) {
				displayNames.map { name ->
					localArtistsByName[artistCreditIdentityKey(name)] ?: artist.asSyntheticArtistCredit(name)
				}
			} else {
				listOf(artist)
			}
			artistsForCredit.forEach { resolvedArtist ->
				if (seen.add(artistCreditIdentityKey(resolvedArtist.name))) {
					expanded += resolvedArtist
				}
			}
		}
		return expanded
	}

	private fun DomainArtist.toArtistCreditContext(): ArtistCreditContext =
		ArtistCreditContext(
			originalCredit = name,
			sourceId = id
		)

	private fun DomainArtist.asSyntheticArtistCredit(name: String): DomainArtist =
		copy(
			id = aurralNameLookupArtistId(name),
			name = name,
			albumCount = 0,
			coverArtId = null,
			artistImageUrl = null,
			starredAt = null,
			userRating = null,
			sortName = name,
			musicBrainzId = null,
			lastFmUrl = null,
			roles = emptyList(),
			biography = null,
			similarArtistIds = emptyList()
		)

	private fun ArtistCreditContext.artistCreditAttemptKey(): String =
		artistCreditIdentityKey(originalCredit)

	private fun List<DomainArtist>.artistCreditListKey(): String =
		joinToString("\u001F") { artist ->
			listOf(artist.id, artist.name).joinToString("\u001E") { artistCreditIdentityKey(it) }
		}

	private fun UiState<ImmutableList<DomainArtist>>.withArtistData(
		artists: ImmutableList<DomainArtist>
	): UiState<ImmutableList<DomainArtist>> =
		when (this) {
			is UiState.Loading -> UiState.Loading(artists)
			is UiState.Success -> UiState.Success(artists)
			is UiState.Error -> UiState.Error(error = error, data = artists)
		}

	private companion object {
		const val AURRAL_ARTIST_PHOTO_SEARCH_LIMIT = 5
		const val ARTIST_LIST_AURRAL_ARTWORK_TAG = "ArtistListAurralArtwork"
	}
}
