package paige.navic.ui.screens.bindery

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.ui.core.UiState

class BinderyCatalogViewModel(
	private val path: String,
	private val repository: BinderyRepository
) : ViewModel() {
	private val _catalogState = MutableStateFlow<UiState<BinderyCatalog>>(UiState.Loading())
	val catalogState = _catalogState.asStateFlow()

	val gridState = LazyGridState()
	private var catalogJob: Job? = null
	private var nextPageJob: Job? = null
	private var relatedCatalogJob: Job? = null
	private var nextPagePath: String? = null
	private val _isLoadingNextPage = MutableStateFlow(false)
	val isLoadingNextPage = _isLoadingNextPage.asStateFlow()
	private val _hasNextPage = MutableStateFlow(false)
	val hasNextPage = _hasNextPage.asStateFlow()
	private val _relatedCollectionsState = MutableStateFlow<UiState<BinderyCatalog>>(UiState.Success(BinderyCatalog(title = "")))
	val relatedCollectionsState = _relatedCollectionsState.asStateFlow()
	private val _actionError = MutableStateFlow<Throwable?>(null)
	val actionError = _actionError.asStateFlow()
	private val _actionInFlight = MutableStateFlow<Set<String>>(emptySet())
	val actionInFlight = _actionInFlight.asStateFlow()
	private val collectionArtworkResolver = BinderyCollectionArtworkResolver(repository, viewModelScope)
	val collectionArtworkByPath = collectionArtworkResolver.artworkByPath

	fun refreshCatalog(
		fullRefresh: Boolean,
		languageFilter: String? = null,
		queryMode: BinderyAvailabilityQueryMode = BinderyAvailabilityQueryMode.List
	) {
		catalogJob?.cancel()
		nextPageJob?.cancel()
		_isLoadingNextPage.value = false
		if (fullRefresh) {
			collectionArtworkResolver.clear()
		}
		catalogJob = viewModelScope.launch {
			val currentData = _catalogState.value.data
			if (fullRefresh || currentData == null) {
				_catalogState.value = UiState.Loading(currentData)
			}
			val requestedPath = binderyAvailabilityFilteredCatalogPath(
				path = binderyInitialCatalogPath(path),
				languageFilter = languageFilter,
				mode = queryMode
			)
			repository.getCatalog(requestedPath).fold(
				onSuccess = { catalog ->
					nextPagePath = catalog.nextPagePath()
					_hasNextPage.value = nextPagePath != null
					_catalogState.value = UiState.Success(catalog)
				},
				onFailure = { error ->
					nextPagePath = currentData?.nextPagePath()
					_hasNextPage.value = nextPagePath != null
					_catalogState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = currentData
					)
				}
			)
		}
	}

	fun clearCatalog() {
		catalogJob?.cancel()
		catalogJob = null
		nextPageJob?.cancel()
		nextPageJob = null
		relatedCatalogJob?.cancel()
		relatedCatalogJob = null
		nextPagePath = null
		_isLoadingNextPage.value = false
		_hasNextPage.value = false
		_relatedCollectionsState.value = UiState.Success(BinderyCatalog(title = ""))
		collectionArtworkResolver.clear()
		_catalogState.value = UiState.Success(BinderyCatalog(title = ""))
	}

	fun loadNextPage() {
		if (_isLoadingNextPage.value) return
		val requestedPath = nextPagePath ?: return
		_isLoadingNextPage.value = true
		nextPageJob = viewModelScope.launch {
			val currentData = _catalogState.value.data
			repository.getCatalog(requestedPath).fold(
				onSuccess = { nextPage ->
					val merged = (currentData ?: BinderyCatalog(title = nextPage.title))
						.appendCatalogPage(nextPage)
					nextPagePath = merged.nextPagePath()
					_hasNextPage.value = nextPagePath != null
					_isLoadingNextPage.value = false
					_catalogState.value = UiState.Success(merged)
				},
				onFailure = { error ->
					nextPagePath = requestedPath
					_hasNextPage.value = false
					_isLoadingNextPage.value = false
					_catalogState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = currentData
					)
				}
			)
		}
	}

	fun resolveCollectionArtwork(card: BinderyCatalogCard.Link) {
		collectionArtworkResolver.resolve(card)
	}

	fun refreshRelatedCollections(
		path: String?,
		fullRefresh: Boolean,
		languageFilter: String? = null
	) {
		val requestedPath = path?.trim()?.takeIf { it.isNotEmpty() }
		if (requestedPath == null) {
			relatedCatalogJob?.cancel()
			relatedCatalogJob = null
			_relatedCollectionsState.value = UiState.Success(BinderyCatalog(title = ""))
			return
		}
		relatedCatalogJob?.cancel()
		relatedCatalogJob = viewModelScope.launch {
			val currentData = _relatedCollectionsState.value.data
			if (fullRefresh || currentData == null || currentData.navigation.isEmpty()) {
				_relatedCollectionsState.value = UiState.Loading(currentData)
			}
			repository.getCatalog(
				binderyAvailabilityFilteredCatalogPath(
					path = requestedPath,
					languageFilter = languageFilter,
					mode = BinderyAvailabilityQueryMode.Detail
				)
			).fold(
				onSuccess = { catalog ->
					_relatedCollectionsState.value = UiState.Success(catalog)
				},
				onFailure = { error ->
					_relatedCollectionsState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = currentData
					)
				}
			)
		}
	}

	fun clearError() {
		_catalogState.value = _catalogState.value.data?.let { UiState.Success(it) }
			?: UiState.Loading()
		_relatedCollectionsState.value = _relatedCollectionsState.value.data?.let { UiState.Success(it) }
			?: UiState.Success(BinderyCatalog(title = ""))
	}

	fun performAction(
		link: BinderyLink,
		languageFilter: String? = null,
		queryMode: BinderyAvailabilityQueryMode = BinderyAvailabilityQueryMode.List,
		relatedCollectionsPath: String? = null
	) {
		val actionPath = link.href.trim().takeIf { it.isNotEmpty() } ?: return
		if (actionPath in _actionInFlight.value) return
		viewModelScope.launch {
			_actionInFlight.value = _actionInFlight.value + actionPath
			repository.performAction(actionPath).fold(
				onSuccess = {
					_actionInFlight.value = _actionInFlight.value - actionPath
					refreshCatalog(
						fullRefresh = true,
						languageFilter = languageFilter,
						queryMode = queryMode
					)
					refreshRelatedCollections(
						path = relatedCollectionsPath,
						fullRefresh = true,
						languageFilter = languageFilter
					)
				},
				onFailure = { error ->
					_actionInFlight.value = _actionInFlight.value - actionPath
					_actionError.value = error
				}
			)
		}
	}

	fun clearActionError() {
		_actionError.value = null
	}
}
