package paige.navic.ui.screens.bindery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.ui.core.UiState

data class BinderyBookData(
	val manifest: BinderyManifest,
	val resources: BinderyResourceCatalog = BinderyResourceCatalog(title = "Resources"),
	val findings: BinderyCatalog = BinderyCatalog(title = "Findings")
)

class BinderyBookViewModel(
	private val bookId: String,
	private val repository: BinderyRepository
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
		bookJob = viewModelScope.launch {
			val currentData = _bookState.value.data
			if (fullRefresh || currentData == null) {
				_bookState.value = UiState.Loading(currentData)
			}
			repository.getManifest(bookId).fold(
				onSuccess = { manifest ->
					val findings = repository.getBookFindings(bookId).getOrElse {
						BinderyCatalog(title = "Findings")
					}
					repository.getBookResources(bookId).fold(
						onSuccess = { resources ->
							_bookState.value = UiState.Success(
								BinderyBookData(
									manifest = manifest,
									resources = resources,
									findings = findings
								)
							)
						},
						onFailure = { error ->
							_bookState.value = UiState.Error(
								error = error as? Exception ?: Exception(error),
								data = BinderyBookData(
									manifest = manifest,
									findings = findings
								)
							)
						}
					)
				},
				onFailure = { error ->
					_bookState.value = UiState.Error(
						error = error as? Exception ?: Exception(error),
						data = currentData
					)
				}
			)
		}
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
		viewModelScope.launch {
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

	fun clearActionError() {
		_actionError.value = null
	}
}
