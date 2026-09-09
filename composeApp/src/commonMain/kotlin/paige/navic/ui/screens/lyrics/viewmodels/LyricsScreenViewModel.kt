package paige.navic.ui.screens.lyrics.viewmodels

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.lyrics.LyricsResult
import paige.navic.domain.repositories.LyricsRepository
import paige.navic.ui.core.UiState
import paige.navic.ui.core.EnrichmentRequestOwner

class LyricsScreenViewModel(
	private val song: DomainSong?,
	private val repository: LyricsRepository
) : ViewModel() {
	val lyricsState: StateFlow<UiState<LyricsResult?>>
		field = MutableStateFlow<UiState<LyricsResult?>>(UiState.Loading())

	val listState = LazyListState()
	private var visualContentActive = false
	private var refreshPending = true
	private val lookup = EnrichmentRequestOwner()

	fun setVisualContentActive(active: Boolean) {
		visualContentActive = active
		if (!active && lookup.cancel() && lyricsState.value is UiState.Loading) {
			refreshPending = true
		}
		if (active && (refreshPending || lyricsState.value is UiState.Error)) refreshResults()
	}

	fun refreshResults() {
		if (!visualContentActive) {
			refreshPending = true
			return
		}
		if (lookup.isRunning) return
		refreshPending = false
		lookup.launch(viewModelScope) {
			if (song == null) {
				lookup.commit { lyricsState.value = UiState.Success(null) }
				return@launch
			}
			lookup.commit { lyricsState.value = UiState.Loading(lyricsState.value.data) }
			try {
				val result = repository.fetchLyrics(song)
				lookup.commit { lyricsState.value = UiState.Success(result) }
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (e: Exception) {
				lookup.commit { lyricsState.value = UiState.Error(e, lyricsState.value.data) }
			}
		}
	}
}
