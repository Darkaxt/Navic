package paige.navic.ui.screens.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_genres
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalBottomBarScrollManager
import paige.navic.ui.components.layouts.ArtGrid
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.components.snackbars.ErrorSnackbar
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.genre.components.GenreListScreenSearchField
import paige.navic.ui.screens.genre.components.GenreListScreenSortButton
import paige.navic.ui.screens.genre.components.genreListScreenContent
import paige.navic.ui.screens.genre.viewmodels.GenreListViewModel
import paige.navic.util.ui.withoutTop

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GenreListScreen(
	nested: Boolean
) {
	val viewModel = koinViewModel<GenreListViewModel>()
	val genresState by viewModel.visibleGenresState.collectAsState()
	val selectedSorting by viewModel.selectedSorting.collectAsState()
	val selectedReversed by viewModel.selectedReversed.collectAsState()
	// Text field state stays in Compose so typing never waits on a flow round-trip.
	var searchQuery by rememberSaveable { mutableStateOf(viewModel.searchQuery.value) }
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
	val actions: @Composable RowScope.() -> Unit = {
		GenreListScreenSortButton(
			nested = nested,
			selectedSorting = selectedSorting,
			onSetSorting = { viewModel.setSorting(it) },
			selectedReversed = selectedReversed,
			onSetReversed = { viewModel.setReversed(it) }
		)
	}

	Scaffold(
		topBar = {
			if (!nested) {
				RootTopBar(
					{ Text(stringResource(Res.string.title_genres)) },
					scrollBehavior,
					actions
				)
			} else {
				NestedTopBar({ Text(stringResource(Res.string.title_genres)) }, actions)
			}
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			if (!nested) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface)
		) {
			GenreListScreenSearchField(
				query = searchQuery,
				onQueryChange = { query ->
					searchQuery = query
					viewModel.setSearchQuery(query)
				},
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
			)
			PullToRefreshBox(
				modifier = Modifier.weight(1f),
				finished = genresState !is UiState.Loading,
				onRefresh = { viewModel.refreshGenres(true) },
				key = genresState
			) {
				ArtGrid(
					modifier = if (!nested)
						Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
					else Modifier,
					contentPadding = innerPadding.withoutTop(),
					state = viewModel.gridState,
					verticalArrangement = if ((genresState as? UiState.Success)?.data?.isEmpty() == true)
						Arrangement.Center
					else Arrangement.spacedBy(12.dp)
				) {
					genreListScreenContent(
						state = genresState,
						searching = searchQuery.isNotBlank()
					)
				}
			}
		}
	}

	ErrorSnackbar(
		error = (genresState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)
}
