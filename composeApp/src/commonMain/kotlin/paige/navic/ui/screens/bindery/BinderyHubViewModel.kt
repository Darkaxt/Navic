package paige.navic.ui.screens.bindery

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.OptionalIntegrationResult
import paige.navic.domain.models.failureOrNull
import paige.navic.domain.repositories.BinderyAudiobookVersion
import paige.navic.domain.repositories.BinderyBookSync
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.reader.decodeReaderReadingProgress
import paige.navic.ui.core.UiState

data class BinderyHubState(
	val root: BinderyCatalog,
	val rows: List<BinderyHubCatalogRow>,
	val continueListening: List<BinderyContinueListeningItem> = emptyList(),
	val continueReading: List<BinderyContinueReadingItem> = emptyList(),
	/** Bindery's Whispersync-ready audiobooks (`whispersync=ready`), every page; null until loaded. */
	val whispersync: BinderyCatalog? = null
)

data class BinderyHubCatalogRow(
	val row: BinderyHubRow,
	val catalog: BinderyCatalog
) {
	fun cards(languageFilter: String? = null): List<BinderyCatalogCard> =
		binderyCatalogCards(catalog, row.catalogTab)
			.let { cards ->
				if (row.kind.showOnlyAvailableContent()) {
					cards.filter { card -> card.hasAvailableContent(languageFilter) }
				} else {
					cards
				}
			}
}

fun BinderyHubState.whispersyncReadyAudiobooks(languageFilter: String? = null): List<BinderyCatalogCard.Book> {
	val audiobooks = rows.firstOrNull { hubRow -> hubRow.row.kind == BinderyHubRowKind.Audiobooks }
	// Without the filtered feed (not loaded yet, or an older Bindery) the Audiobooks row stands in.
	val filtered = whispersync?.let { catalog -> audiobooks?.copy(catalog = catalog) }
	return (filtered?.let(::listOf) ?: rows).whispersyncReadyAudiobookCards(languageFilter)
}

/** Route ids of the newest books in the hub's Recently Added row, the same ids a card tap opens. */
fun BinderyHubState.recentlyAddedBookIds(
	languageFilter: String? = null,
	limit: Int = BinderyRecentlyAddedPrefetchLimit
): List<String> =
	rows.firstOrNull { hubRow -> hubRow.row.kind == BinderyHubRowKind.RecentlyAdded }
		?.cards(languageFilter)
		.orEmpty()
		.filterIsInstance<BinderyCatalogCard.Book>()
		.take(limit)
		.map { card -> binderyBookRouteId(card.id) }

internal const val BinderyRecentlyAddedPrefetchLimit = 6

fun List<BinderyHubCatalogRow>.whispersyncReadyAudiobookCards(
	languageFilter: String? = null
): List<BinderyCatalogCard.Book> =
	firstOrNull { hubRow -> hubRow.row.kind == BinderyHubRowKind.Audiobooks }
		?.cards(languageFilter)
		.orEmpty()
		.filterIsInstance<BinderyCatalogCard.Book>()
		.filter { card -> card.hasActionableWhispersync }

/** The catalog followed through its next pages (Bindery pages feeds at 50 entries by default). */
internal suspend fun BinderyCatalog.withFollowingPages(
	maxPages: Int = BinderyHubAudiobookPageLimit,
	fetch: suspend (String) -> BinderyCatalog?
): BinderyCatalog {
	var merged = this
	var pages = 1
	val visited = mutableSetOf<String>()
	var next = merged.nextPagePath()
	while (next != null && pages < maxPages && visited.add(next)) {
		val page = fetch(next) ?: break
		merged = merged.appendCatalogPage(page)
		pages++
		next = merged.nextPagePath()
	}
	return merged
}

internal const val BinderyHubAudiobookPageLimit = 20

/** The Audiobooks feed narrowed by Bindery to books with a ready Whispersync pair. */
internal fun binderyWhispersyncCatalogPath(audiobookRow: BinderyHubRow, languageFilter: String?): String {
	val path = binderyAvailabilityFilteredCatalogPath(
		path = audiobookRow.path,
		languageFilter = languageFilter,
		mode = BinderyAvailabilityQueryMode.List
	)
	return if ('?' in path) "$path&whispersync=ready" else "$path?whispersync=ready"
}

/** The hub with [BinderyHubState.whispersync] loaded through [fetch]; unchanged when it fails. */
internal suspend fun BinderyHubState.withWhispersyncCatalog(
	languageFilter: String?,
	fetch: suspend (String) -> BinderyCatalog?
): BinderyHubState {
	val audiobooks = rows.firstOrNull { hubRow -> hubRow.row.kind == BinderyHubRowKind.Audiobooks } ?: return this
	val catalog = fetch(binderyWhispersyncCatalogPath(audiobooks.row, languageFilter))
		?.withFollowingPages(fetch = fetch)
		?: return this
	return copy(whispersync = catalog)
}

private fun BinderyHubRowKind.showOnlyAvailableContent(): Boolean =
	when (this) {
		BinderyHubRowKind.LastRead,
		BinderyHubRowKind.RecentlyAdded,
		BinderyHubRowKind.MostPopular,
		BinderyHubRowKind.Audiobooks -> true
		BinderyHubRowKind.Genres,
		BinderyHubRowKind.Authors,
		BinderyHubRowKind.Collections,
		BinderyHubRowKind.Findings,
		BinderyHubRowKind.Wanted -> false
	}

class BinderyHubViewModel(
	private val repository: BinderyRepository,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	private val _hubState = MutableStateFlow<UiState<BinderyHubState>>(UiState.Loading())
	val hubState = _hubState.asStateFlow()
	private val _hubAvailability = MutableStateFlow<OptionalIntegrationResult<BinderyHubState>?>(null)
	val hubAvailability = _hubAvailability.asStateFlow()

	val gridState = LazyGridState()
	private var hubJob: Job? = null
	private val collectionArtworkResolver = BinderyCollectionArtworkResolver(repository, viewModelScope)
	val collectionArtworkByPath = collectionArtworkResolver.artworkByPath

	fun refreshHub(
		fullRefresh: Boolean,
		languageFilter: String? = null
	) {
		hubJob?.cancel()
		if (fullRefresh) {
			collectionArtworkResolver.clear()
		}
		hubJob = viewModelScope.launch(Dispatchers.IO) {
			val currentData = _hubState.value.data
			val cachedData = if (!fullRefresh && currentData == null) {
				loadCachedHub(languageFilter)
			} else {
				null
			}
			if (cachedData != null) {
				_hubState.value = UiState.Success(cachedData)
			}
			val visibleData = cachedData ?: currentData
			if (fullRefresh || visibleData == null) {
				_hubState.value = UiState.Loading(visibleData)
			}
			val repositoryResult = loadHub(languageFilter)
			val result = if (
				repositoryResult is OptionalIntegrationResult.Unavailable &&
				visibleData != null
			) {
				OptionalIntegrationResult.Stale(
					data = visibleData,
					failure = repositoryResult.failure
				)
			} else {
				repositoryResult
			}
			_hubAvailability.value = result
			// Shown at once with the cached Whispersync feed; the live one follows below.
			val firstPages = when (result) {
				is OptionalIntegrationResult.Available -> result.data
				is OptionalIntegrationResult.Stale -> result.data
				else -> null
			}
			val pagedData = firstPages?.withWhispersyncCatalog(languageFilter, ::cachedCatalogPage)
			when (result) {
				is OptionalIntegrationResult.Available -> {
					val state = pagedData ?: result.data
					val currentState = _hubState.value
					if (currentState !is UiState.Success || currentState.data != state) {
						_hubState.value = UiState.Success(state)
					}
				}
				OptionalIntegrationResult.Empty -> {
					_hubState.value = UiState.Success(emptyBinderyHubState())
				}
				is OptionalIntegrationResult.Stale -> {
					_hubState.value = UiState.Success(pagedData ?: result.data)
				}
				is OptionalIntegrationResult.Unavailable -> {
					_hubState.value = UiState.Error(
						error = IllegalStateException(result.failure.message),
						data = visibleData
					)
				}
			}
			if (firstPages != null && pagedData != null && result !is OptionalIntegrationResult.Stale) {
				val livePaged = firstPages.withWhispersyncCatalog(languageFilter) { path ->
					repository.getCatalog(path).getOrNull()
				}
				val shown = _hubState.value
				// A failed live fetch keeps whatever the cache already showed.
				if (
					livePaged.whispersync != null &&
					livePaged != pagedData &&
					shown is UiState.Success && shown.data == pagedData
				) {
					_hubState.value = UiState.Success(livePaged)
				}
			}
		}
	}

	fun clearHub() {
		hubJob?.cancel()
		hubJob = null
		collectionArtworkResolver.clear()
		_hubAvailability.value = null
		_hubState.value = UiState.Success(
			BinderyHubState(
				root = BinderyCatalog(title = ""),
				rows = emptyList()
			)
		)
	}

	fun clearError() {
		_hubState.value = _hubState.value.data?.let { UiState.Success(it) }
			?: UiState.Loading()
	}

	fun resolveCollectionArtwork(card: BinderyCatalogCard.Link) {
		collectionArtworkResolver.resolve(card)
	}

	private suspend fun loadCachedHub(languageFilter: String?): BinderyHubState? {
		val rootCatalog = repository.getCachedCatalog("/").getOrNull() ?: return null
		val rows = binderyHubRows(rootCatalog).mapNotNull { row ->
			repository.getCachedCatalog(
				binderyAvailabilityFilteredCatalogPath(
					path = row.catalogPath,
					languageFilter = languageFilter,
					mode = BinderyAvailabilityQueryMode.List
				)
			).getOrNull()?.let { catalog ->
				BinderyHubCatalogRow(row, catalog)
			}
		}
		return BinderyHubState(
			root = rootCatalog,
			rows = rows,
			continueListening = loadContinueListening(refreshMetadata = false),
			continueReading = loadContinueReading(
				refreshMetadata = false,
				languageFilter = languageFilter
			)
		).withWhispersyncCatalog(languageFilter, ::cachedCatalogPage)
	}

	private suspend fun cachedCatalogPage(path: String): BinderyCatalog? =
		repository.getCachedCatalog(path).getOrNull()

	private suspend fun loadHub(languageFilter: String?): OptionalIntegrationResult<BinderyHubState> {
		val rootResult = repository.getCatalogOptional("/")
		val rootCatalog = when (rootResult) {
			is OptionalIntegrationResult.Available -> rootResult.data
			is OptionalIntegrationResult.Stale -> rootResult.data
			OptionalIntegrationResult.Empty -> return OptionalIntegrationResult.Empty
			is OptionalIntegrationResult.Unavailable -> return rootResult
		}
		val rowResults = coroutineScope {
			binderyHubRows(rootCatalog).map { row ->
				async {
					row to repository.getCatalogOptional(
						binderyAvailabilityFilteredCatalogPath(
							path = row.catalogPath,
							languageFilter = languageFilter,
							mode = BinderyAvailabilityQueryMode.List
						)
					)
				}
			}.awaitAll()
		}
		val catalogs = rowResults.mapNotNull { (row, result) ->
			when (result) {
				is OptionalIntegrationResult.Available -> BinderyHubCatalogRow(row, result.data)
				is OptionalIntegrationResult.Stale -> BinderyHubCatalogRow(row, result.data)
				OptionalIntegrationResult.Empty,
				is OptionalIntegrationResult.Unavailable -> null
			}
		}
		val state = BinderyHubState(
			root = rootCatalog,
			rows = catalogs,
			continueListening = loadContinueListening(refreshMetadata = true),
			continueReading = loadContinueReading(
				refreshMetadata = true,
				languageFilter = languageFilter
			)
		)
		val failure = rootResult.failureOrNull()
			?: rowResults.firstNotNullOfOrNull { (_, result) -> result.failureOrNull() }
		return failure?.let { OptionalIntegrationResult.Stale(state, it) }
			?: OptionalIntegrationResult.Available(state)
	}

	private suspend fun loadContinueListening(
		refreshMetadata: Boolean
	): List<BinderyContinueListeningItem> {
		val progresses = binderyAudiobookProgressEntries(preferenceManager.binderyAudiobookProgressJson)
		val companionProgresses = binderyWhispersyncCompanionProgressEntries(
			preferenceManager.binderyWhispersyncCompanionProgressJson
		)
		if (progresses.isEmpty() && companionProgresses.isEmpty()) return emptyList()
		val bookIds = (progresses.map { it.bookId } + companionProgresses.map { it.bookId })
			.mapNotNull(String::safeProgressToken)
			.distinct()
		val manifestsByBookId = loadManifestsByBookId(bookIds, refreshMetadata)
		val versionsByBookId = loadAudiobookVersionsByBookId(bookIds, refreshMetadata)
		val detailsById = buildMap {
			versionsByBookId.values.flatten().forEach { version ->
				version.id?.toString()?.safeProgressToken()?.let { put(it, version) }
			}
			val audiobookIds = (progresses.map { it.versionRowId } + companionProgresses.map { it.audiobookId })
				.mapNotNull(String::safeProgressToken)
				.distinct()
			audiobookIds.forEach { audiobookId ->
				if (containsKey(audiobookId)) return@forEach
				loadAudiobookDetail(audiobookId, refreshMetadata)?.let { put(audiobookId, it) }
			}
		}
		return binderyContinueListeningItems(
			progresses = progresses,
			companionProgresses = companionProgresses,
			manifestsByBookId = manifestsByBookId,
			audiobookDetailsById = detailsById
		)
	}

	private suspend fun loadContinueReading(
		refreshMetadata: Boolean,
		languageFilter: String?
	): List<BinderyContinueReadingItem> {
		val progresses = decodeReaderReadingProgress(preferenceManager.readerReadingProgressJson)
		if (progresses.isEmpty()) return emptyList()
		val bookIds = progresses.map { it.bookId }
			.mapNotNull(String::safeProgressToken)
			.distinct()
		val manifestsByBookId = loadManifestsByBookId(bookIds, refreshMetadata)
		val resourcesByBookId = bookIds.mapNotNull { bookId ->
			loadBookResources(bookId, refreshMetadata)?.let { bookId to it }
		}.toMap()
		val versionsByBookId = loadAudiobookVersionsByBookId(bookIds, refreshMetadata)
		val syncByBookId = bookIds.mapNotNull { bookId ->
			loadBookSync(bookId, refreshMetadata)?.let { bookId to it }
		}.toMap()
		return binderyContinueReadingItems(
			progresses = progresses,
			manifestsByBookId = manifestsByBookId,
			resourcesByBookId = resourcesByBookId,
			audiobookVersionsByBookId = versionsByBookId,
			syncByBookId = syncByBookId,
			languageFilter = languageFilter,
			opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl
		)
	}

	private suspend fun loadManifestsByBookId(
		bookIds: List<String>,
		refreshMetadata: Boolean
	): Map<String, BinderyManifest> =
		bookIds.mapNotNull { bookId ->
			val manifest = if (refreshMetadata) {
				repository.getManifest(bookId, forceRefresh = true).getOrElse {
					repository.getCachedManifest(bookId).getOrNull()
				}
			} else {
				repository.getCachedManifest(bookId).getOrNull()
			}
			manifest?.let { bookId to it }
		}.toMap()

	private suspend fun loadAudiobookVersionsByBookId(
		bookIds: List<String>,
		refreshMetadata: Boolean
	): Map<String, List<BinderyAudiobookVersion>> =
		bookIds.mapNotNull { bookId ->
			val versions = if (refreshMetadata) {
				repository.getAudiobookVersions(bookId, forceRefresh = true).getOrElse {
					repository.getCachedAudiobookVersions(bookId).getOrNull().orEmpty()
				}
			} else {
				repository.getCachedAudiobookVersions(bookId).getOrNull().orEmpty()
			}
			(bookId to versions).takeIf { versions.isNotEmpty() }
		}.toMap()

	private suspend fun loadAudiobookDetail(
		audiobookId: String,
		refreshMetadata: Boolean
	): BinderyAudiobookVersion? =
		if (refreshMetadata) {
			repository.getAudiobookDetail(audiobookId, forceRefresh = true).getOrElse {
				repository.getCachedAudiobookDetail(audiobookId).getOrNull()
			}
		} else {
			repository.getCachedAudiobookDetail(audiobookId).getOrNull()
		}

	private suspend fun loadBookResources(
		bookId: String,
		refreshMetadata: Boolean
	): BinderyResourceCatalog? =
		if (refreshMetadata) {
			repository.getBookResources(bookId, forceRefresh = true).getOrElse {
				repository.getCachedBookResources(bookId).getOrNull()
			}
		} else {
			repository.getCachedBookResources(bookId).getOrNull()
		}

	private suspend fun loadBookSync(
		bookId: String,
		refreshMetadata: Boolean
	): BinderyBookSync? =
		if (refreshMetadata) {
			repository.getBookSync(bookId, forceRefresh = true).getOrElse {
				repository.getCachedBookSync(bookId).getOrNull()
			}
		} else {
			repository.getCachedBookSync(bookId).getOrNull()
		}
}

private fun String.safeProgressToken(): String? =
	trim().takeIf { it.isNotEmpty() }

private fun emptyBinderyHubState(): BinderyHubState = BinderyHubState(
	root = BinderyCatalog(title = ""),
	rows = emptyList()
)
