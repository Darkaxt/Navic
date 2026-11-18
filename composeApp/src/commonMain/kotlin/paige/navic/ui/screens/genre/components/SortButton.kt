package paige.navic.ui.screens.genre.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import paige.navic.LocalPlatformContext
import paige.navic.domain.models.DomainGenreListType
import paige.navic.ui.screens.genre.genreListSortOptions
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Sort
import paige.navic.ui.components.layouts.TopBarButton
import paige.navic.ui.components.sheets.SortSheet

@Composable
fun GenreListScreenSortButton(
	nested: Boolean,
	selectedSorting: DomainGenreListType,
	onSetSorting: (DomainGenreListType) -> Unit,
	selectedReversed: Boolean,
	onSetReversed: (Boolean) -> Unit
) {
	val platformContext = LocalPlatformContext.current
	val entries = remember { genreListSortOptions() }
	var expanded by remember { mutableStateOf(false) }
	if (!nested) {
		IconButton(onClick = {
			platformContext.clickSound()
			expanded = true
		}) {
			Icon(
				Icons.Outlined.Sort,
				contentDescription = null
			)
		}
	} else {
		TopBarButton({ expanded = true }) {
			Icon(
				Icons.Outlined.Sort,
				contentDescription = null
			)
		}
	}
	if (expanded) {
		SortSheet(
			entries = entries,
			selectedSorting = selectedSorting,
			selectedReversed = selectedReversed,
			label = { stringResource(it.displayName) },
			onSetSorting = onSetSorting,
			onSetReversed = onSetReversed,
			onDismissRequest = { expanded = false }
		)
	}
}
