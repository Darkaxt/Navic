package paige.navic.ui.screens.genre.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SyncManager
import paige.navic.domain.models.DomainGenreListType
import paige.navic.domain.models.DomainGenreSummary
import paige.navic.domain.repositories.GenreRepository
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.genre.visibleGenreSummaries

class GenreListViewModel(
	private val repository: GenreRepository,
	private val sessionManager: SessionManager,
	private val syncManager: SyncManager,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	val genresState: StateFlow<UiState<ImmutableList<DomainGenreSummary>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainGenreSummary>>>(UiState.Loading())

	private val _selectedSorting = MutableStateFlow(preferenceManager.genreListSorting)
	val selectedSorting = _selectedSorting.asStateFlow()

	private val _selectedReversed = MutableStateFlow(preferenceManager.genreListReversed)
	val selectedReversed = _selectedReversed.asStateFlow()

	private val _searchQuery = MutableStateFlow("")
	val searchQuery = _searchQuery.asStateFlow()

	/** Genre list as shown on the genres screen: filtered by [searchQuery] and ordered by the
	 *  persisted sort. [genresState] stays in repository order for the library row. */
	val visibleGenresState: StateFlow<UiState<ImmutableList<DomainGenreSummary>>> =
		combine(genresState, _selectedSorting, _selectedReversed, _searchQuery) { state, sorting, reversed, query ->
			state.mapGenres { genres -> visibleGenreSummaries(genres, sorting, reversed, query) }
		}
			.flowOn(Dispatchers.Default)
			.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading())

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

	fun setSorting(sorting: DomainGenreListType) {
		_selectedSorting.value = sorting
		preferenceManager.genreListSorting = sorting
	}

	fun setReversed(reversed: Boolean) {
		_selectedReversed.value = reversed
		preferenceManager.genreListReversed = reversed
	}

	fun setSearchQuery(query: String) {
		_searchQuery.value = query
	}

	fun clearError() {
		genresState.value = UiState.Success(genresState.value.data ?: persistentListOf())
	}

	private fun Throwable.asException(): Exception = this as? Exception ?: Exception(this)

	private inline fun UiState<ImmutableList<DomainGenreSummary>>.mapGenres(
		transform: (ImmutableList<DomainGenreSummary>) -> ImmutableList<DomainGenreSummary>
	): UiState<ImmutableList<DomainGenreSummary>> = when (this) {
		is UiState.Loading -> UiState.Loading(data?.let(transform))
		is UiState.Success -> UiState.Success(transform(data))
		is UiState.Error -> UiState.Error(error, data?.let(transform))
	}
}
