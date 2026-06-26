package paige.navic.ui.screens.artist.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.data.database.entities.ArtistPhotoCacheEntity
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.models.ArtistCreditContext
import paige.navic.domain.models.ArtistCreditResolution
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainArtistListType
import paige.navic.domain.models.artistCreditIdentityKey
import paige.navic.domain.models.splitArtistCredit
import paige.navic.domain.repositories.ArtistCreditResolutionRepository
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.ArtistRepository
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.screens.artist.artistListAurralPhotoCacheEntity
import paige.navic.ui.screens.artist.artistListAurralPhotoCandidate
import paige.navic.ui.screens.artist.artistListAurralPhotoHydrationTargets
import paige.navic.ui.screens.artist.artistCreditResolvedRows
import paige.navic.ui.screens.artist.toArtistHeaderImageCacheEntry
import paige.navic.ui.screens.artist.withCachedArtistPhoto
import paige.navic.ui.core.UiState
import paige.navic.util.core.Logger
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
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
	private val _listType = MutableStateFlow(initialListType)
	val listType = _listType.asStateFlow()

	private val _selectedReversed = MutableStateFlow(false)
	val selectedReversed = _selectedReversed.asStateFlow()

	private val _isRefreshing = MutableStateFlow(false)
	private val _refreshError = MutableStateFlow<Exception?>(null)

	/** Reactive raw artist list (before photo/credit enrichment); re-derives on
	 *  listType/reversed change and auto-updates on Room changes. */
	private val reactiveArtists: kotlinx.coroutines.flow.Flow<ImmutableList<DomainArtist>> =
		combine(_listType, _selectedReversed) { listType, reversed -> listType to reversed }
			.distinctUntilChanged()
			.flatMapLatest { (listType, reversed) ->
				repository.artistsFlow(listType, reversed)
			}

	/** Backing state set by the long-lived [observeArtists] collector (runs hydration). */
	private val _artistsState =
		MutableStateFlow<UiState<ImmutableList<DomainArtist>>>(UiState.Loading())

	/** Public state: derived from the collector snapshot + refresh loading/error. */
	val artistsState: kotlinx.coroutines.flow.StateFlow<UiState<ImmutableList<DomainArtist>>> =
		combine(_artistsState, _isRefreshing, _refreshError) { state, refreshing, error ->
			when {
				error != null -> UiState.Error(error, state.data)
				refreshing -> UiState.Loading(state.data)
				else -> state
			}
		}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading())

	private val _starred = MutableStateFlow(false)
	val starred = _starred.asStateFlow()

	private val _selectedArtist = MutableStateFlow<DomainArtist?>(null)
	val selectedArtist = _selectedArtist.asStateFlow()

	private val _selectedArtistAlbums = MutableStateFlow<ImmutableList<DomainAlbum>?>(null)
	val selectedArtistAlbums = _selectedArtistAlbums.asStateFlow()

	val gridState = LazyGridState()
	private val attemptedAurralArtistPhotoKeys = mutableSetOf<String>()
	private val attemptedArtistCreditKeys = mutableSetOf<String>()
	private val artistCreditResolutionState =
		MutableStateFlow<Map<String, ArtistCreditResolution>>(emptyMap())
	private var lastRawArtistKey: String = ""
	private var artistsCollectorJob: Job? = null

	init {
		observeArtists()
	}

	/**
	 * Long-lived reactive collector: combines the shared artist flow with the photo cache
	 * and credit resolutions, builds the display snapshot, and runs Aurral photo / credit
	 * hydration as a side-effect. Subscribed once (VM-scoped), so no per-visit re-query.
	 */
	private fun observeArtists() {
		artistsCollectorJob?.cancel()
		artistsCollectorJob = viewModelScope.launch {
			combine(
				reactiveArtists,
				artistPhotoCacheDao.observeArtistPhotoCache(),
				artistCreditResolutionState
			) { rawArtists: ImmutableList<DomainArtist>,
				cachedPhotos: List<ArtistPhotoCacheEntity>,
				creditResolutions: Map<String, ArtistCreditResolution> ->
				val entries = cachedPhotos.map { entry -> entry.toArtistHeaderImageCacheEntry() }
				val displayedArtists = rawArtists
					.withKnownArtistCreditRows(creditResolutions)
					.withCachedArtistPhotos(entries)
					.toImmutableList()
				ArtistListSnapshot(
					state = UiState.Success(displayedArtists),
					rawArtists = rawArtists
				)
			}
				.flowOn(Dispatchers.Default)
				.collect { snapshot ->
					_artistsState.value = snapshot.state
					val rawArtists = snapshot.rawArtists
					lastRawArtistKey = rawArtists.artistCreditListKey()
					hydrateAurralArtistPhotos(snapshot.state.data.orEmpty())
					hydrateArtistCreditResolutions(rawArtists)
				}
		}
	}

	fun refreshArtists(fullRefresh: Boolean) {
		if (fullRefresh) {
			attemptedAurralArtistPhotoKeys.clear()
			attemptedArtistCreditKeys.clear()
			viewModelScope.launch {
				_isRefreshing.value = true
				_refreshError.value = runCatching { repository.syncArtists() }.exceptionOrNull() as? Exception
				_isRefreshing.value = false
			}
		}
		// The reactive observeArtists collector auto-updates from Room changes after sync.
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
	}

	fun setReversed(reversed: Boolean) {
		_selectedReversed.value = reversed
	}

	fun clearError() {
		_refreshError.value = null
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

	private fun List<DomainArtist>.withKnownArtistCreditRows(
		resolutions: Map<String, ArtistCreditResolution>
	): List<DomainArtist> =
		artistCreditResolvedRows(this) { artist ->
			resolutions[artist.artistCreditAttemptKey()]
		}

	private fun hydrateAurralArtistPhotos(artists: List<DomainArtist>) {
		val targets = artistListAurralPhotoHydrationTargets(
			artists = artists,
			attemptedLookupKeys = attemptedAurralArtistPhotoKeys,
			externalArtworkEnabled = preferenceManager.aurralEnabled,
			limit = AURRAL_ARTIST_PHOTO_BATCH_LIMIT
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
		val targets = artists
			.asSequence()
			.mapNotNull { artist ->
				val context = artist.toArtistCreditContext()
				if (splitArtistCredit(context.originalCredit).size <= 1) return@mapNotNull null
				context.takeIf { attemptedArtistCreditKeys.add(it.artistCreditAttemptKey()) }
			}
			.take(ARTIST_CREDIT_RESOLUTION_BATCH_LIMIT)
			.toList()
		if (targets.isEmpty()) return

		viewModelScope.launch(Dispatchers.IO) {
			val cachedUpdates = mutableMapOf<String, ArtistCreditResolution>()
			val uncachedTargets = mutableListOf<ArtistCreditContext>()
			targets.forEach { context ->
				val resolution = artistCreditResolutionRepository.cachedResolution(context)
				if (resolution != null) {
					cachedUpdates[context.artistCreditAttemptKey()] = resolution
				} else {
					uncachedTargets += context
				}
			}
			publishArtistCreditResolutionUpdates(cachedUpdates)

			val resolvedUpdates = mutableMapOf<String, ArtistCreditResolution>()
			uncachedTargets.forEach { context ->
				val resolution = artistCreditResolutionRepository.resolveAndCache(context) ?: return@forEach
				resolvedUpdates[context.artistCreditAttemptKey()] = resolution
				if (resolvedUpdates.size >= ARTIST_CREDIT_RESOLUTION_PUBLISH_BATCH_SIZE) {
					publishArtistCreditResolutionUpdates(resolvedUpdates.toMap())
					resolvedUpdates.clear()
				}
			}
			publishArtistCreditResolutionUpdates(resolvedUpdates)
			if (lastRawArtistKey == sourceKey) {
				viewModelScope.launch {
					hydrateAurralArtistPhotos(_artistsState.value.data.orEmpty())
					hydrateArtistCreditResolutions(artists)
				}
			}
		}
	}

	private fun publishArtistCreditResolutionUpdates(
		updates: Map<String, ArtistCreditResolution>
	) {
		if (updates.isEmpty()) return
		artistCreditResolutionState.value = artistCreditResolutionState.value + updates
	}

	private fun DomainArtist.toArtistCreditContext(): ArtistCreditContext =
		ArtistCreditContext(
			originalCredit = name,
			sourceId = id
		)

	private fun ArtistCreditContext.artistCreditAttemptKey(): String =
		artistCreditIdentityKey(originalCredit)

	private fun DomainArtist.artistCreditAttemptKey(): String =
		artistCreditIdentityKey(name)

	private fun List<DomainArtist>.artistCreditListKey(): String =
		joinToString("\u001F") { artist ->
			listOf(artist.id, artist.name).joinToString("\u001E") { artistCreditIdentityKey(it) }
		}

	private companion object {
		const val AURRAL_ARTIST_PHOTO_BATCH_LIMIT = 24
		const val ARTIST_CREDIT_RESOLUTION_BATCH_LIMIT = 24
		const val ARTIST_CREDIT_RESOLUTION_PUBLISH_BATCH_SIZE = 8
		const val AURRAL_ARTIST_PHOTO_SEARCH_LIMIT = 5
		const val ARTIST_LIST_AURRAL_ARTWORK_TAG = "ArtistListAurralArtwork"
	}
}

private data class ArtistListSnapshot(
	val state: UiState<ImmutableList<DomainArtist>>,
	val rawArtists: List<DomainArtist>
)
