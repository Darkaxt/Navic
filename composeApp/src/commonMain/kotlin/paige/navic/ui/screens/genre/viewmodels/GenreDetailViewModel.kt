package paige.navic.ui.screens.genre.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import paige.navic.domain.manager.SyncManager
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainGenre
import paige.navic.domain.models.DomainGenreCollection
import paige.navic.domain.models.genreAlbums
import paige.navic.domain.models.genreArtistsFromAlbums
import paige.navic.domain.models.genrePlayableSongsFromAlbums
import paige.navic.domain.models.genreTotalDuration
import paige.navic.domain.models.toPlaybackOrigin
import paige.navic.domain.repositories.GenreRepository
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.core.UiState
import kotlin.time.Duration

@Immutable
data class GenreDetailState(
	val genre: DomainGenre,
	val artists: List<DomainArtist>,
	val albums: List<DomainAlbum>,
	val collection: DomainGenreCollection,
	val totalDuration: Duration
)

class GenreDetailViewModel(
	private val genreName: String,
	private val genreRepository: GenreRepository,
	private val syncManager: SyncManager
) : ViewModel() {
	private val _genreState = MutableStateFlow<UiState<GenreDetailState>>(UiState.Loading())
	val genreState = _genreState.asStateFlow()
	private var observeGenreJob: Job? = null
	private var refreshGenreJob: Job? = null
	private var isRefreshing = false

	init {
		observeGenre()
	}

	fun refreshGenre(fullRefresh: Boolean) {
		observeGenre()
		if (!fullRefresh || refreshGenreJob?.isActive == true) return

		refreshGenreJob = viewModelScope.launch {
			isRefreshing = true
			_genreState.value = UiState.Loading(_genreState.value.data)
			val result = syncManager.syncNow()
			isRefreshing = false
			val latestData = _genreState.value.data
			_genreState.value = result.fold(
				onSuccess = {
					latestData?.let { UiState.Success(it) }
						?: UiState.Error(genreNotFoundError())
				},
				onFailure = { error -> UiState.Error(error.asException(), latestData) }
			)
		}
	}

	private fun observeGenre() {
		if (observeGenreJob?.isActive == true) return
		observeGenreJob = viewModelScope.launch {
			genreRepository.observeGenreByName(genreName)
				.map { genre -> genre?.toDetailState() }
				.flowOn(Dispatchers.Default)
				.collect { state ->
					_genreState.value = when {
						state != null && isRefreshing -> UiState.Loading(state)
						state != null -> UiState.Success(state)
						isRefreshing -> UiState.Loading(_genreState.value.data)
						else -> UiState.Error(genreNotFoundError(), _genreState.value.data)
					}
				}
		}
	}

	fun play(player: MediaPlayerViewModel) {
		val state = (_genreState.value as? UiState.Success)?.data ?: return
		player.clearQueue()
		player.setPlaybackOrigin(state.genre.toPlaybackOrigin())
		player.addToQueue(state.collection)
		player.playAt(0)
	}

	fun shuffle(player: MediaPlayerViewModel) {
		val state = (_genreState.value as? UiState.Success)?.data ?: return
		player.setPlaybackOrigin(state.genre.toPlaybackOrigin())
		player.shufflePlay(state.collection)
	}

	fun playNext(player: MediaPlayerViewModel) {
		val state = (_genreState.value as? UiState.Success)?.data ?: return
		player.playNext(state.collection)
	}

	fun addToQueue(player: MediaPlayerViewModel) {
		val state = (_genreState.value as? UiState.Success)?.data ?: return
		player.addToQueue(state.collection)
	}

	private fun DomainGenre.toDetailState(): GenreDetailState {
		val albums = genreAlbums(this)
		val songs = genrePlayableSongsFromAlbums(albums)
		val totalDuration = genreTotalDuration(songs)
		val collection = DomainGenreCollection(
			id = name,
			name = name,
			coverArtId = albums.firstOrNull()?.coverArtId,
			duration = totalDuration,
			songCount = songs.size,
			songs = songs
		)
		return GenreDetailState(
			genre = this,
			artists = genreArtistsFromAlbums(albums),
			albums = albums,
			collection = collection,
			totalDuration = totalDuration
		)
	}

	private fun genreNotFoundError() = IllegalStateException("Genre '$genreName' was not found")

	private fun Throwable.asException(): Exception = this as? Exception ?: Exception(this)
}
