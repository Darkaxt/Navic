package paige.navic.ui.screens.genre.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SyncManager
import paige.navic.domain.models.DomainGenreSummary
import paige.navic.domain.repositories.GenreRepository
import paige.navic.ui.core.UiState

class GenreListViewModel(
	private val repository: GenreRepository,
	private val sessionManager: SessionManager,
	private val syncManager: SyncManager
) : ViewModel() {
	val genresState: StateFlow<UiState<ImmutableList<DomainGenreSummary>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainGenreSummary>>>(UiState.Loading())

	private var observeGenresJob: Job? = null
	private var refreshGenresJob: Job? = null
	private var isRefreshing = false

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { loggedIn ->
				if (loggedIn) {
					observeGenres()
				} else {
					observeGenresJob?.cancel()
					genresState.value = UiState.Loading()
				}
			}
		}
	}

	fun refreshGenres(fullRefresh: Boolean) {
		observeGenres()
		if (!fullRefresh || refreshGenresJob?.isActive == true) return

		refreshGenresJob = viewModelScope.launch {
			isRefreshing = true
			genresState.value = UiState.Loading(genresState.value.data)
			val result = syncManager.syncNow()
			isRefreshing = false
			val latestData = genresState.value.data ?: persistentListOf()
			genresState.value = result.fold(
				onSuccess = { UiState.Success(latestData) },
				onFailure = { error ->
					UiState.Error(error.asException(), latestData)
				}
			)
		}
	}

	private fun observeGenres() {
		if (observeGenresJob?.isActive == true) return
		observeGenresJob = viewModelScope.launch {
			repository.observeGenreSummaries().collect { genres ->
				genresState.value = if (isRefreshing) {
					UiState.Loading(genres)
				} else {
					UiState.Success(genres)
				}
			}
		}
	}

	fun clearError() {
		genresState.value = UiState.Success(genresState.value.data ?: persistentListOf())
	}

	private fun Throwable.asException(): Exception = this as? Exception ?: Exception(this)
}
