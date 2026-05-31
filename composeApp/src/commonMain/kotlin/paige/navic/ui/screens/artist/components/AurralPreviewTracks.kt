package paige.navic.ui.screens.artist.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import paige.navic.domain.models.AurralPreviewTrack

@Composable
expect fun AurralPreviewTracks(
	title: String,
	tracks: ImmutableList<AurralPreviewTrack>,
	modifier: Modifier
)
