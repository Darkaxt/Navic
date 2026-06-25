package paige.navic.ui.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_hide_row
import navic.composeapp.generated.resources.action_reorder
import navic.composeapp.generated.resources.action_show_row
import navic.composeapp.generated.resources.option_sort_newest
import navic.composeapp.generated.resources.option_sort_quick_picks
import navic.composeapp.generated.resources.option_sort_recent
import navic.composeapp.generated.resources.option_sort_starred
import navic.composeapp.generated.resources.title_artists
import navic.composeapp.generated.resources.title_aurral_based_on_library
import navic.composeapp.generated.resources.title_aurral_explore_by_tag
import navic.composeapp.generated.resources.title_aurral_genre_rows
import navic.composeapp.generated.resources.title_aurral_global_top
import navic.composeapp.generated.resources.title_aurral_recently_added
import navic.composeapp.generated.resources.title_aurral_recent_releases
import navic.composeapp.generated.resources.title_aurral_recommended_for_you
import navic.composeapp.generated.resources.title_genres
import navic.composeapp.generated.resources.title_library_row_order
import navic.composeapp.generated.resources.title_most_played
import navic.composeapp.generated.resources.title_playlists
import navic.composeapp.generated.resources.title_stations
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.icons.Icons
import paige.navic.icons.outlined.DragHandle
import paige.navic.icons.outlined.Visibility
import paige.navic.icons.outlined.VisibilityOff
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.screens.library.LibraryRowId
import paige.navic.ui.screens.library.effectiveLibraryRowOrder
import paige.navic.ui.screens.library.hiddenLibraryRows
import paige.navic.ui.screens.library.libraryRowHiddenPreference
import paige.navic.ui.screens.library.libraryRowOrderPreference
import paige.navic.ui.screens.library.moveLibraryRow
import paige.navic.util.ui.DraggableListState
import paige.navic.util.ui.dragHandle
import paige.navic.util.ui.draggableItems
import paige.navic.util.ui.rememberDraggableListState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsLibraryRowsScreen() {
	val platformContext = LocalPlatformContext.current
	val haptic = LocalHapticFeedback.current
	val preferenceManager = koinInject<PreferenceManager>()
	var rows by remember(preferenceManager.libraryRowOrder) {
		mutableStateOf(effectiveLibraryRowOrder(preferenceManager.libraryRowOrder))
	}
	var hiddenRows by remember(preferenceManager.libraryHiddenRows) {
		mutableStateOf(hiddenLibraryRows(preferenceManager.libraryHiddenRows))
	}
	val draggableState = rememberDraggableListState { from, to ->
		rows = moveLibraryRow(rows, from, to)
		preferenceManager.libraryRowOrder = libraryRowOrderPreference(rows)
		haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
	}

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_library_row_order)) },
				hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
			)
		},
		contentWindowInsets = WindowInsets.statusBars
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier.padding(innerPadding),
			state = draggableState.listState,
			contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			draggableItems(
				state = draggableState,
				items = rows,
				key = { row -> row.preferenceId }
			) { row, isDragging ->
				LibraryRowSettingsItem(
					row = row,
					state = draggableState,
					hidden = row in hiddenRows,
					isDragging = isDragging,
					onToggleVisibility = {
						platformContext.clickSound()
						hiddenRows = if (row in hiddenRows) {
							hiddenRows - row
						} else {
							hiddenRows + row
						}
						preferenceManager.libraryHiddenRows = libraryRowHiddenPreference(hiddenRows)
					}
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryRowSettingsItem(
	row: LibraryRowId,
	state: DraggableListState,
	hidden: Boolean,
	isDragging: Boolean,
	onToggleVisibility: () -> Unit
) {
	val elevation by animateDpAsState(
		if (isDragging) 4.dp else 0.dp,
		animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
	)

	Surface(
		shadowElevation = elevation,
		modifier = Modifier.fillMaxWidth(),
		shape = MaterialTheme.shapes.large,
		color = MaterialTheme.colorScheme.surfaceContainer
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 8.dp, vertical = 6.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			IconButton(onClick = onToggleVisibility) {
				Icon(
					if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
					contentDescription = stringResource(
						if (hidden) Res.string.action_show_row else Res.string.action_hide_row
					)
				)
			}
			Column(Modifier.weight(1f)) {
				Text(stringResource(row.titleResource()))
			}
			IconButton(
				modifier = Modifier.dragHandle(
					state = state,
					key = row.preferenceId
				),
				onClick = {}
			) {
				Icon(
					Icons.Outlined.DragHandle,
					contentDescription = stringResource(Res.string.action_reorder)
				)
			}
		}
	}
}

private fun LibraryRowId.titleResource(): StringResource =
	when (this) {
		LibraryRowId.QuickPicks -> Res.string.option_sort_quick_picks
		LibraryRowId.MostPlayed -> Res.string.title_most_played
		LibraryRowId.NewestAlbums -> Res.string.option_sort_newest
		LibraryRowId.StarredAlbums -> Res.string.option_sort_starred
		LibraryRowId.RecentAlbums -> Res.string.option_sort_recent
		LibraryRowId.Stations -> Res.string.title_stations
		LibraryRowId.Playlists -> Res.string.title_playlists
		LibraryRowId.Artists -> Res.string.title_artists
		LibraryRowId.Genres -> Res.string.title_genres
		LibraryRowId.AurralRecentlyAdded -> Res.string.title_aurral_recently_added
		LibraryRowId.AurralRecentReleases -> Res.string.title_aurral_recent_releases
		LibraryRowId.AurralRecommended -> Res.string.title_aurral_recommended_for_you
		LibraryRowId.AurralBasedOnLibrary -> Res.string.title_aurral_based_on_library
		LibraryRowId.AurralGlobalTop -> Res.string.title_aurral_global_top
		LibraryRowId.AurralGenreRows -> Res.string.title_aurral_genre_rows
		LibraryRowId.AurralTags -> Res.string.title_aurral_explore_by_tag
	}
