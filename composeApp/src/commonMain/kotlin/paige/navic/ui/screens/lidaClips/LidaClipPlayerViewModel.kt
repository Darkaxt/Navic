package paige.navic.ui.screens.lidaClips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.repositories.CollectionRepository
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.ui.core.UiState

class LidaClipPlayerViewModel(
	private val songId: String,
	private val collectionRepository: CollectionRepository,
	private val repository: LidaClipsRepository
) : ViewModel() {
	private val _clipState = MutableStateFlow<UiState<DomainLidaClip?>>(UiState.Loading())
	val clipState = _clipState.asStateFlow()

	init {
		load()
	}

	fun load(forceRefresh: Boolean = false) {
		viewModelScope.launch(Dispatchers.IO) {
			_clipState.value = UiState.Loading(_clipState.value.data)
			val song = runCatching { collectionRepository.getSongById(songId) }.getOrNull()
			val result = if (song != null) {
				repository.findClipForSong(
					song = song,
					forceRefresh = forceRefresh
				)
			} else {
				repository.findClipByNavidromeSongId(
					songId = songId,
					forceRefresh = forceRefresh
				)
			}
			_clipState.value = result.fold(
				onSuccess = { UiState.Success(it) },
				onFailure = { UiState.Error(Exception(it), _clipState.value.data) }
			)
		}
	}
}
