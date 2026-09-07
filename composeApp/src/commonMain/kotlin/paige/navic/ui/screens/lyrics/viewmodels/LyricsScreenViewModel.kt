package paige.navic.ui.screens.lyrics.viewmodels

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.lyrics.LyricsResult
import paige.navic.domain.repositories.LyricsRepository
import paige.navic.ui.core.UiState

class LyricsScreenViewModel(
	private val song: DomainSong?,
	private val repository: LyricsRepository
) : ViewModel() {
	val lyricsState: StateFlow<UiState<LyricsResult?>>
		field = MutableStateFlow<UiState<LyricsResult?>>(UiState.Loading())

	val listState = LazyListState()
	private var visualContentActive = false
	private var refreshPending = true
	private var lookupJob: Job? = null

	fun setVisualContentActive(active: Boolean) {
		visualContentActive = active
		if (active && (refreshPending || lyricsState.value is UiState.Error)) refreshResults()
	}

	fun refreshResults() {
		if (!visualContentActive) {
			refreshPending = true
			return
		}
		if (lookupJob?.isActive == true) return
		refreshPending = false
		lookupJob = viewModelScope.launch {
			if (song == null) {
				lyricsState.value = UiState.Success(null)
				return@launch
			}
			lyricsState.value = UiState.Loading()
			try {
				lyricsState.value = UiState.Success(
					repository.fetchLyrics(song)
				)
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (e: Exception) {
				lyricsState.value = UiState.Error(e)
			}
		}
	}
}
