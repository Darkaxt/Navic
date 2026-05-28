package paige.navic.ui.screens.collection.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.models.DomainPlaylistSongSortType
import paige.navic.domain.models.playlistSongSortOptions
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Sort
import paige.navic.ui.components.layouts.TopBarButton
import paige.navic.ui.components.sheets.SortSheet

@Composable
fun CollectionDetailScreenSortButton(
	selectedSorting: DomainPlaylistSongSortType,
	onSetSorting: (DomainPlaylistSongSortType) -> Unit,
	selectedReversed: Boolean,
	onSetReversed: (Boolean) -> Unit
) {
	val entries = remember { playlistSongSortOptions() }
	var expanded by remember { mutableStateOf(false) }
	TopBarButton({ expanded = true }) {
		Icon(
			Icons.Outlined.Sort,
			contentDescription = null
		)
	}
	if (expanded) {
		SortSheet(
			entries = entries,
			onDismissRequest = { expanded = false },
			selectedSorting = selectedSorting,
			onSetSorting = onSetSorting,
			selectedReversed = selectedReversed,
			label = { stringResource(it.displayName) },
			onSetReversed = onSetReversed
		)
	}
}
