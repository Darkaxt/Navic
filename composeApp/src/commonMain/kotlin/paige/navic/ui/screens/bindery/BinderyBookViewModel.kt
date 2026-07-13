package paige.navic.ui.screens.bindery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.manager.ShareManager
import paige.navic.domain.repositories.BinderyAudiobookVersion
import paige.navic.domain.repositories.BinderyBookSync
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.ui.core.UiState

data class BinderyBookData(
	val manifest: BinderyManifest,
	val resources: BinderyResourceCatalog = BinderyResourceCatalog(title = "Resources"),
	val audiobooks: List<BinderyAudiobookVersion> = emptyList(),
	val sync: BinderyBookSync = BinderyBookSync(),
	val findings: BinderyCatalog = BinderyCatalog(title = "Findings")
)

class BinderyBookViewModel(
	private val bookId: String,
	private val repository: BinderyRepository,
	private val shareManager: ShareManager
) : ViewModel() {
	private val _bookState = MutableStateFlow<UiState<BinderyBookData>>(UiState.Loading())
	val bookState = _bookState.asStateFlow()
	private val _actionError = MutableStateFlow<Throwable?>(null)
	val actionError = _actionError.asStateFlow()
	private val _actionInFlight = MutableStateFlow<Set<String>>(emptySet())
	val actionInFlight = _actionInFlight.asStateFlow()

	private var bookJob: Job? = null

	fun refreshBook(fullRefresh: Boolean) {
		bookJob?.cancel()
		bookJob = viewModelScope.launch(Dispatchers.IO) {
			val currentData = _bookState.value.data
			val cachedData = if (!fullRefresh && currentData == null) cachedBookDataOrNull() else null
			if (cachedData != null) {
				_bookState.value = UiState.Success(cachedData)
			}
			val visibleData = cachedData ?: currentData
			if (fullRefresh || visibleData == null) {
				_bookState.value = UiState.Loading(visibleData)
			}
			repository.getManifest(bookId, forceRefresh = fullRefresh).fold(
				onSuccess = { manifest ->
					val audiobooks = repository.getAudiobookVersions(
						bookId,
						forceRefresh = fullRefresh
					).getOrElse {
						emptyList()
					}
					val resources = repository.getBookResources(
						bookId,
						forceRefresh = fullRefresh
					).getOrElse {
						BinderyResourceCatalog(title = "Resources")
					}
					val sync = repository.getBookSync(bookId, forceRefresh = fullRefresh).getOrElse {
						manifest.sync ?: BinderyBookSync(bookId = bookId.toLongOrNull())
					}
					val freshData =
						BinderyBookData(
							manifest = manifest,
							resources = resources,
							audiobooks = audiobooks,
							sync = sync
						)
					val state = _bookState.value
					if (state !is UiState.Success || state.data != freshData) {
						_bookState.value = UiState.Success(freshData)
					}
				},
				onFailure = { error ->
					_bookState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = visibleData
					)
				}
			)
		}
	}

	private suspend fun cachedBookDataOrNull(): BinderyBookData? {
		val manifest = repository.getCachedManifest(bookId).getOrNull() ?: return null
		val audiobooks = repository.getCachedAudiobookVersions(bookId).getOrNull().orEmpty()
		val resources = repository.getCachedBookResources(bookId).getOrNull()
			?: BinderyResourceCatalog(title = "Resources")
		val sync = repository.getCachedBookSync(bookId).getOrNull()
			?: manifest.sync
			?: BinderyBookSync(bookId = bookId.toLongOrNull())
		return BinderyBookData(
			manifest = manifest,
			resources = resources,
			audiobooks = audiobooks,
			sync = sync
		)
	}

	fun clearBook() {
		bookJob?.cancel()
		bookJob = null
		_bookState.value = UiState.Success(
			BinderyBookData(
				manifest = BinderyManifest(
					id = bookId,
					title = ""
				)
			)
		)
	}

	fun clearError() {
		_bookState.value = _bookState.value.data?.let { UiState.Success(it) } ?: UiState.Loading()
	}

	fun performAction(link: BinderyLink) {
		val actionPath = link.href.trim().takeIf { it.isNotEmpty() } ?: return
		if (actionPath in _actionInFlight.value) return
		viewModelScope.launch(Dispatchers.IO) {
			_actionInFlight.value = _actionInFlight.value + actionPath
			repository.performAction(actionPath).fold(
				onSuccess = {
					_actionInFlight.value = _actionInFlight.value - actionPath
					refreshBook(fullRefresh = true)
				},
				onFailure = { error ->
					_actionInFlight.value = _actionInFlight.value - actionPath
					_actionError.value = error
				}
			)
		}
	}

	fun downloadVersionResource(row: BinderyBookVersionRow) {
		val resourcePath = row.id.trim().takeIf { it.isNotEmpty() } ?: return
		if (resourcePath in _actionInFlight.value) return
		viewModelScope.launch(Dispatchers.IO) {
			_actionInFlight.value = _actionInFlight.value + resourcePath
			repository.getResourceBytes(resourcePath).fold(
				onSuccess = { bytes ->
					try {
						shareManager.shareFile(
							fileName = row.downloadFileName(),
							mimeType = row.downloadMimeType(),
							bytes = bytes
						)
					} catch (error: Throwable) {
						_actionError.value = error
					} finally {
						_actionInFlight.value = _actionInFlight.value - resourcePath
					}
				},
				onFailure = { error ->
					_actionInFlight.value = _actionInFlight.value - resourcePath
					_actionError.value = error
				}
			)
		}
	}

	fun clearActionError() {
		_actionError.value = null
	}
}
