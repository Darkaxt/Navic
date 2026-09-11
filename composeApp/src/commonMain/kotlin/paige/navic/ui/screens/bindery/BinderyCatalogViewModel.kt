package paige.navic.ui.screens.bindery

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyPublication
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.ui.core.UiState
import paige.navic.util.core.synchronized

class BinderyCatalogViewModel(
	private val path: String,
	private val repository: BinderyRepository,
	private val actionDispatcher: CoroutineDispatcher = Dispatchers.IO,
	private val catalogDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
	private val _catalogState = MutableStateFlow<UiState<BinderyCatalog>>(UiState.Loading())
	val catalogState = _catalogState.asStateFlow()

	val gridState = LazyGridState()
	private var catalogJob: Job? = null
	private var nextPageJob: Job? = null
	private var relatedCatalogJob: Job? = null
	private val catalogOwnershipLock = Any()
	private var catalogGeneration = 0L
	private var activeMutation: CatalogMutationClaim? = null
	private var activePaginationGeneration: Long? = null
	private var rootCatalogPath: String? = null
	private val catalogPages = linkedMapOf<String, BinderyCatalog>()
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
		val request = claimCatalogRequest() ?: return
		catalogJob?.cancel()
		if (fullRefresh) {
			collectionArtworkResolver.clear()
		}
		catalogJob = viewModelScope.launch(catalogDispatcher) {
			val requestedPath = binderyAvailabilityFilteredCatalogPath(
				path = binderyInitialCatalogPath(path),
				languageFilter = languageFilter,
				mode = queryMode
			)
			val currentData = _catalogState.value.data
			val cachedData = if (!fullRefresh && currentData == null) {
				repository.getCachedCatalog(requestedPath).getOrNull()
			} else {
				null
			}
			if (cachedData != null) {
				if (!commitCatalogRequest(request) {
					replaceCatalogPages(requestedPath, cachedData)
					nextPagePath = cachedData.nextPagePath()
					_hasNextPage.value = nextPagePath != null
					_catalogState.value = UiState.Success(cachedData)
				}) return@launch
			}
			val visibleData = cachedData ?: currentData
			if (fullRefresh || visibleData == null) {
				if (!commitCatalogRequest(request) {
					_catalogState.value = UiState.Loading(visibleData)
				}) return@launch
			}
			val result = repository.getCatalog(requestedPath, forceRefresh = fullRefresh)
			val catalog = result.getOrNull()
			if (catalog != null) {
				commitCatalogRequest(request) {
					replaceCatalogPages(requestedPath, catalog)
					nextPagePath = catalog.nextPagePath()
					_hasNextPage.value = nextPagePath != null
					val state = _catalogState.value
					if (state !is UiState.Success || state.data != catalog) {
						_catalogState.value = UiState.Success(catalog)
					}
				}
			} else {
				val error = result.exceptionOrNull() ?: return@launch
				commitCatalogRequest(request) {
					nextPagePath = visibleData?.nextPagePath()
					_hasNextPage.value = nextPagePath != null
					_catalogState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = visibleData
					)
				}
			}
		}
	}

	fun clearCatalog() {
		invalidateCatalogOwnership()
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
		val claim = claimPagination() ?: return
		catalogJob?.cancel()
		catalogJob = null
		nextPageJob = viewModelScope.launch(catalogDispatcher) {
			val currentData = _catalogState.value.data
			try {
				if (!isPaginationCurrent(claim)) return@launch
				val result = repository.getCatalog(claim.path)
				val nextPage = result.getOrNull()
				if (nextPage != null) {
					commitPagination(claim) {
						val previousPage = catalogPages.put(claim.path, nextPage)
						val merged = rebuildMergedCatalogPages()
						if (merged == null) {
							if (previousPage == null) catalogPages.remove(claim.path) else catalogPages[claim.path] = previousPage
						} else {
							nextPagePath = merged.nextPagePath()
							_hasNextPage.value = nextPagePath != null
							_catalogState.value = UiState.Success(merged)
						}
					}
				} else {
					val error = result.exceptionOrNull() ?: return@launch
					commitPagination(claim) {
						nextPagePath = claim.path
						_hasNextPage.value = false
						_catalogState.value = UiState.Error(
							error = error as? Exception ?: Exception(error),
							data = currentData
						)
					}
				}
			} finally {
				finishPagination(claim)
			}
		}
	}

	fun resolveCollectionArtwork(card: BinderyCatalogCard.Link) {
		collectionArtworkResolver.resolve(card)
	}

	fun refreshRelatedCollections(
		path: String?,
		fullRefresh: Boolean,
		languageFilter: String? = null,
		showLoading: Boolean = true
	) {
		val requestedPath = path?.trim()?.takeIf { it.isNotEmpty() }
		if (requestedPath == null) {
			relatedCatalogJob?.cancel()
			relatedCatalogJob = null
			_relatedCollectionsState.value = UiState.Success(BinderyCatalog(title = ""))
			return
		}
		relatedCatalogJob?.cancel()
		relatedCatalogJob = viewModelScope.launch(Dispatchers.IO) {
			val filteredPath = binderyAvailabilityFilteredCatalogPath(
				path = requestedPath,
				languageFilter = languageFilter,
				mode = BinderyAvailabilityQueryMode.Detail
			)
			val currentData = _relatedCollectionsState.value.data
			val needsInitialData = currentData == null || currentData.navigation.isEmpty()
			val cachedData = if (!fullRefresh && needsInitialData) {
				repository.getCachedCatalog(filteredPath).getOrNull()
			} else {
				null
			}
			if (cachedData != null) {
				_relatedCollectionsState.value = UiState.Success(cachedData)
			}
			val visibleData = cachedData ?: currentData
			if (showLoading && (fullRefresh || visibleData == null || visibleData.navigation.isEmpty())) {
				_relatedCollectionsState.value = UiState.Loading(visibleData)
			}
			repository.getCatalog(filteredPath, forceRefresh = fullRefresh).fold(
				onSuccess = { catalog ->
					val state = _relatedCollectionsState.value
					if (state !is UiState.Success || state.data != catalog) {
						_relatedCollectionsState.value = UiState.Success(catalog)
					}
				},
				onFailure = { error ->
					_relatedCollectionsState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = visibleData
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
		val claim = claimMutation(actionPath) ?: return
		catalogJob?.cancel()
		catalogJob = null
		viewModelScope.launch(actionDispatcher) {
			try {
				val actionFailure = repository.performAction(actionPath).exceptionOrNull()
				if (actionFailure != null) {
					commitMutation(claim) { _actionError.value = binderyActionFailure() }
					return@launch
				}
				if (!isMutationCurrent(claim)) return@launch

				val result = repository.getCatalog(claim.sourcePath, forceRefresh = true)
				val catalog = result.getOrNull()
				var confirmed = false
				if (catalog != null) {
					val committed = commitMutation(claim) {
						if (catalog.confirms(claim.confirmation)) {
							val previousPage = catalogPages.put(claim.sourcePath, catalog)
							val merged = rebuildMergedCatalogPages()
							if (merged == null) {
								if (previousPage == null) {
									catalogPages.remove(claim.sourcePath)
								} else {
									catalogPages[claim.sourcePath] = previousPage
								}
								_actionError.value = binderyActionNotConfirmed()
							} else {
								nextPagePath = merged.nextPagePath()
								_hasNextPage.value = nextPagePath != null
								_catalogState.value = UiState.Success(merged)
								confirmed = true
							}
						} else {
							_actionError.value = binderyActionNotConfirmed()
						}
					}
					if (committed && confirmed) {
						refreshRelatedCollections(
							path = relatedCollectionsPath,
							fullRefresh = true,
							languageFilter = languageFilter,
							showLoading = false
						)
					}
				} else {
					commitMutation(claim) { _actionError.value = binderyActionNotConfirmed() }
				}
			} finally {
				releaseMutation(claim)
			}
		}
	}

	private fun claimCatalogRequest(): CatalogRequestClaim? = synchronized(catalogOwnershipLock) {
		if (activeMutation != null) return@synchronized null
		nextPageJob?.cancel()
		nextPageJob = null
		activePaginationGeneration = null
		_isLoadingNextPage.value = false
		CatalogRequestClaim(++catalogGeneration)
	}

	private suspend fun commitCatalogRequest(
		claim: CatalogRequestClaim,
		block: () -> Unit
	): Boolean {
		val context = currentCoroutineContext()
		context.ensureActive()
		return synchronized(catalogOwnershipLock) {
			if (claim.generation != catalogGeneration || activeMutation != null) {
				false
			} else {
				context.ensureActive()
				block()
				true
			}
		}
	}

	private fun claimPagination(): CatalogPaginationClaim? = synchronized(catalogOwnershipLock) {
		if (activeMutation != null || activePaginationGeneration != null) return@synchronized null
		val path = nextPagePath ?: return@synchronized null
		val generation = ++catalogGeneration
		activePaginationGeneration = generation
		_isLoadingNextPage.value = true
		CatalogPaginationClaim(path, generation)
	}

	private fun isPaginationCurrent(claim: CatalogPaginationClaim): Boolean = synchronized(catalogOwnershipLock) {
		claim.generation == catalogGeneration &&
			activePaginationGeneration == claim.generation &&
			activeMutation == null
	}

	private suspend fun commitPagination(claim: CatalogPaginationClaim, block: () -> Unit): Boolean {
		val context = currentCoroutineContext()
		context.ensureActive()
		return synchronized(catalogOwnershipLock) {
			if (
				claim.generation != catalogGeneration ||
				activePaginationGeneration != claim.generation ||
				activeMutation != null
			) {
				false
			} else {
				context.ensureActive()
				block()
				true
			}
		}
	}

	private fun finishPagination(claim: CatalogPaginationClaim) = synchronized(catalogOwnershipLock) {
		if (
			claim.generation != catalogGeneration ||
			activePaginationGeneration != claim.generation
		) return@synchronized
		activePaginationGeneration = null
		_isLoadingNextPage.value = false
	}

	private fun claimMutation(actionPath: String): CatalogMutationClaim? = synchronized(catalogOwnershipLock) {
		if (activeMutation != null) return@synchronized null
		val actions = catalogPages.flatMap { (sourcePath, catalog) ->
			catalog.findMutationActions(actionPath).map { action -> action.copy(sourcePath = sourcePath) }
		}
		val action = actions.singleOrNull()
		if (action == null) {
			_actionError.value = binderyActionNotConfirmed()
			return@synchronized null
		}
		val confirmation = when (action.type) {
			BinderyOpdsActionType.Monitor,
			BinderyOpdsActionType.Unmonitor -> action.target.exactInverseConfirmation(action.type)
				?: CatalogMutationConfirmation.RefreshedSource
			BinderyOpdsActionType.DownloadRequest -> CatalogMutationConfirmation.RefreshedSource
		}
		val generation = ++catalogGeneration
		nextPageJob?.cancel()
		nextPageJob = null
		activePaginationGeneration = null
		_isLoadingNextPage.value = false
		val claim = CatalogMutationClaim(
			actionPath = actionPath,
			generation = generation,
			sourcePath = action.sourcePath,
			confirmation = confirmation
		)
		activeMutation = claim
		_actionError.value = null
		_actionInFlight.value = setOf(actionPath)
		_catalogState.value.data?.let { currentData ->
			_catalogState.value = UiState.Success(currentData)
		}
		claim
	}

	private fun isMutationCurrent(claim: CatalogMutationClaim): Boolean = synchronized(catalogOwnershipLock) {
		claim.generation == catalogGeneration && activeMutation == claim
	}

	private suspend fun commitMutation(claim: CatalogMutationClaim, block: () -> Unit): Boolean {
		val context = currentCoroutineContext()
		context.ensureActive()
		return synchronized(catalogOwnershipLock) {
			if (claim.generation != catalogGeneration || activeMutation != claim) {
				false
			} else {
				context.ensureActive()
				block()
				true
			}
		}
	}

	private fun releaseMutation(claim: CatalogMutationClaim) = synchronized(catalogOwnershipLock) {
		if (activeMutation != claim) return@synchronized
		activeMutation = null
		_actionInFlight.value = emptySet()
	}

	private fun replaceCatalogPages(sourcePath: String, catalog: BinderyCatalog) {
		rootCatalogPath = sourcePath
		catalogPages.clear()
		catalogPages[sourcePath] = catalog
	}

	private fun rebuildMergedCatalogPages(): BinderyCatalog? {
		val chain = buildCatalogPageChain(
			rootPath = rootCatalogPath ?: return null,
			pages = catalogPages
		) ?: return null
		catalogPages.keys.retainAll(chain.loadedPaths)
		return chain.catalog
	}

	private fun invalidateCatalogOwnership() = synchronized(catalogOwnershipLock) {
		catalogGeneration++
		activeMutation = null
		activePaginationGeneration = null
		rootCatalogPath = null
		catalogPages.clear()
		_actionInFlight.value = emptySet()
		_isLoadingNextPage.value = false
	}

	fun clearActionError() {
		_actionError.value = null
	}
}

private data class CatalogPageChain(
	val catalog: BinderyCatalog,
	val loadedPaths: Set<String>
)

private fun buildCatalogPageChain(
	rootPath: String,
	pages: Map<String, BinderyCatalog>
): CatalogPageChain? {
	var currentPath = rootPath
	var merged: BinderyCatalog? = null
	val visited = linkedSetOf<String>()
	while (true) {
		if (!visited.add(currentPath)) {
			val safeCatalog = merged?.withoutNextPageLink() ?: return null
			return CatalogPageChain(safeCatalog, visited)
		}
		val page = pages[currentPath] ?: return null
		merged = merged?.appendCatalogPage(page) ?: page
		val nextPath = page.nextPagePath() ?: break
		if (nextPath in visited) {
			merged = merged.withoutNextPageLink()
			break
		}
		if (nextPath !in pages) break
		currentPath = nextPath
	}
	return CatalogPageChain(merged, visited)
}

private fun BinderyCatalog.withoutNextPageLink(): BinderyCatalog =
	copy(
		links = links.filterNot { link ->
			link.rel.any { rel -> rel.equals("next", ignoreCase = true) }
		}
	)

private data class CatalogRequestClaim(val generation: Long)

private data class CatalogPaginationClaim(
	val path: String,
	val generation: Long
)

private data class CatalogMutationClaim(
	val actionPath: String,
	val generation: Long,
	val sourcePath: String,
	val confirmation: CatalogMutationConfirmation
)

private data class CatalogMutationAction(
	val type: BinderyOpdsActionType,
	val target: CatalogMutationTarget,
	val sourcePath: String = ""
)

private sealed interface CatalogMutationTarget {
	data object Catalog : CatalogMutationTarget
	data class Navigation(val href: String) : CatalogMutationTarget
	data class Publication(val identity: String?) : CatalogMutationTarget
}

private sealed interface CatalogMutationConfirmation {
	data class ExactInverse(
		val target: CatalogMutationTarget,
		val expectedAction: BinderyOpdsActionType
	) : CatalogMutationConfirmation

	data object RefreshedSource : CatalogMutationConfirmation
}

private fun BinderyCatalog.findMutationActions(actionPath: String): List<CatalogMutationAction> = buildList {
	directPrimaryAction()
		?.takeIf { action -> action.link.href.trim() == actionPath }
		?.let { action -> add(CatalogMutationAction(action.type, CatalogMutationTarget.Catalog)) }
	navigation.forEach { navigationLink ->
		navigationLink.primaryCardAction()
			?.takeIf { action -> action.link.href.trim() == actionPath }
			?.let { action ->
				add(
					CatalogMutationAction(
						type = action.type,
						target = CatalogMutationTarget.Navigation(navigationLink.href.trim())
					)
				)
			}
	}
	publications.forEach { publication ->
		publication.primarySurfaceAction()
			?.takeIf { action -> action.link.href.trim() == actionPath }
			?.let { action ->
				add(
					CatalogMutationAction(
						type = action.type,
						target = CatalogMutationTarget.Publication(publication.stableMutationIdentity())
					)
				)
			}
	}
}

private fun CatalogMutationTarget.exactInverseConfirmation(
	actionType: BinderyOpdsActionType
): CatalogMutationConfirmation.ExactInverse? {
	val hasStableIdentity = when (this) {
		CatalogMutationTarget.Catalog -> true
		is CatalogMutationTarget.Navigation -> href.isNotEmpty()
		is CatalogMutationTarget.Publication -> identity != null
	}
	if (!hasStableIdentity) return null
	val expectedAction = when (actionType) {
		BinderyOpdsActionType.Monitor -> BinderyOpdsActionType.Unmonitor
		BinderyOpdsActionType.Unmonitor -> BinderyOpdsActionType.Monitor
		BinderyOpdsActionType.DownloadRequest -> return null
	}
	return CatalogMutationConfirmation.ExactInverse(this, expectedAction)
}

private fun BinderyCatalog.confirms(confirmation: CatalogMutationConfirmation): Boolean =
	when (confirmation) {
		is CatalogMutationConfirmation.ExactInverse ->
			actionFor(confirmation.target)?.type == confirmation.expectedAction
		CatalogMutationConfirmation.RefreshedSource -> true
	}

private fun BinderyCatalog.actionFor(target: CatalogMutationTarget): BinderyOpdsAction? =
	when (target) {
		CatalogMutationTarget.Catalog -> directPrimaryAction()
		is CatalogMutationTarget.Navigation -> navigation
			.firstOrNull { link -> link.href.trim() == target.href }
			?.primaryCardAction()
		is CatalogMutationTarget.Publication -> target.identity?.let { identity ->
			publications
				.firstOrNull { publication -> publication.stableMutationIdentity() == identity }
				?.primarySurfaceAction()
		}
	}

private fun BinderyCatalog.directPrimaryAction(): BinderyOpdsAction? =
	links.actionWithRel(BINDERY_UNMONITOR_REL, BinderyOpdsActionType.Unmonitor)
		?: links.actionWithRel(BINDERY_MONITOR_REL, BinderyOpdsActionType.Monitor)
		?: links.actionWithRel(BINDERY_DOWNLOAD_REQUEST_REL, BinderyOpdsActionType.DownloadRequest)
			?.takeIf { finding != null }

private fun BinderyLink.primaryCardAction(): BinderyOpdsAction? {
	val actionLinks = listOf(this) + links
	return actionLinks.actionWithRel(BINDERY_UNMONITOR_REL, BinderyOpdsActionType.Unmonitor)
		?: actionLinks.actionWithRel(BINDERY_MONITOR_REL, BinderyOpdsActionType.Monitor)
}

private fun BinderyPublication.primarySurfaceAction(): BinderyOpdsAction? =
	if (isFindingMutationTarget()) {
		links.actionWithRel(BINDERY_DOWNLOAD_REQUEST_REL, BinderyOpdsActionType.DownloadRequest)
			?: primaryAction()
	} else {
		primaryAction()
	}

private fun BinderyPublication.isFindingMutationTarget(): Boolean =
	finding != null ||
		id?.trim()?.startsWith("urn:bindery:finding:", ignoreCase = true) == true ||
		links.any { link -> link.href.contains("/opds/findings", ignoreCase = true) }

private fun BinderyPublication.stableMutationIdentity(): String? {
	id?.trim()?.takeIf { it.isNotEmpty() }?.let { return "id:$it" }
	return links.firstOrNull { link -> link.rel.any { rel -> rel.equals("self", ignoreCase = true) } }
		?.href
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?.let { "self:$it" }
}

private fun List<BinderyLink>.actionWithRel(
	rel: String,
	type: BinderyOpdsActionType
): BinderyOpdsAction? =
	firstOrNull { link -> link.rel.any { item -> item.equals(rel, ignoreCase = true) } }
		?.let { link -> BinderyOpdsAction(type, link) }

private fun binderyActionFailure(): IllegalStateException =
	IllegalStateException("Bindery could not complete this action. Try again.")

private fun binderyActionNotConfirmed(): IllegalStateException =
	IllegalStateException("Bindery did not confirm this action. Try again.")
