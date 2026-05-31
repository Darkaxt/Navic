package paige.navic.ui.screens.artist.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.AurralPreviewTrack

@Composable
actual fun AurralPreviewTracks(
	title: String,
	tracks: ImmutableList<AurralPreviewTrack>,
	modifier: Modifier,
	ownershipStatuses: ImmutableMap<String, AurralOwnershipStatus>
) {
}
