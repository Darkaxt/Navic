package paige.navic.ui.screens.nowPlaying.viewmodels

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import paige.navic.domain.models.DomainSong
import paige.navic.shared.PlaybackStartStatus
import paige.navic.ui.core.PlayerUiState

internal data class VisualEnrichmentDemand(
	val song: DomainSong?,
	val progress: Float,
	val canRequest: Boolean
)

internal fun visualEnrichmentDemand(
	playback: StateFlow<PlayerUiState>,
	visible: StateFlow<Boolean>,
	startup: StateFlow<PlaybackStartStatus?>
) = combine(playback, visible, startup) { state, active, pending ->
	val canRequest = active && pending == null
	VisualEnrichmentDemand(state.currentSong, if (canRequest) state.progress else 0f, canRequest)
}.distinctUntilChanged()
