package paige.navic.ui.screens.genre.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainGenre
import paige.navic.domain.models.DomainGenreCollection
import paige.navic.domain.models.genreAlbums
import paige.navic.domain.models.genreArtists
import paige.navic.domain.models.genreTotalDuration
import paige.navic.domain.models.toPlaybackOrigin
import paige.navic.domain.models.toSongCollection
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
	private val genreRepository: GenreRepository
) : ViewModel() {
	private val _genreState = MutableStateFlow<UiState<GenreDetailState>>(UiState.Loading())
	val genreState = _genreState.asStateFlow()

	init {
		refreshGenre(fullRefresh = false)
	}

	fun refreshGenre(fullRefresh: Boolean) {
		viewModelScope.launch(Dispatchers.IO) {
			val currentData = _genreState.value.data
			if (fullRefresh) {
				_genreState.value = UiState.Loading(currentData)
			}
			runCatching {
				val genre = genreRepository.getGenreByName(genreName, fullRefresh)
					?: error("Genre '$genreName' was not found")
				genre.toDetailState()
			}.onSuccess { state ->
				_genreState.value = UiState.Success(state)
			}.onFailure { error ->
				_genreState.value = UiState.Error(error as? Exception ?: Exception(error), currentData)
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
		val collection = toSongCollection()
		return GenreDetailState(
			genre = this,
			artists = genreArtists(this),
			albums = genreAlbums(this),
			collection = collection,
			totalDuration = genreTotalDuration(this)
		)
	}
}
