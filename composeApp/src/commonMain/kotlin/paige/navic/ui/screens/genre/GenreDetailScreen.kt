package paige.navic.ui.screens.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_add_to_queue
import navic.composeapp.generated.resources.action_play
import navic.composeapp.generated.resources.action_play_next
import navic.composeapp.generated.resources.action_shuffle
import navic.composeapp.generated.resources.count_albums
import navic.composeapp.generated.resources.count_artists
import navic.composeapp.generated.resources.count_songs
import navic.composeapp.generated.resources.info_no_genres
import navic.composeapp.generated.resources.title_albums
import navic.composeapp.generated.resources.title_artists
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.queueTotalDurationLabel
import paige.navic.domain.repositories.aurralRequestHeadersForUrl
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.Genre
import paige.navic.icons.outlined.PlaylistAdd
import paige.navic.icons.outlined.Shuffle
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.layouts.ArtGridItem
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.artist.artistCoverArtIdForExternalArtworkPolicy
import paige.navic.ui.screens.artist.artistImageUrlForExternalArtworkPolicy
import paige.navic.ui.screens.genre.viewmodels.GenreDetailState
import paige.navic.ui.screens.genre.viewmodels.GenreDetailViewModel
import paige.navic.util.ui.withoutTop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailScreen(genreName: String) {
	val viewModel = koinViewModel<GenreDetailViewModel>(
		key = genreName,
		parameters = { parametersOf(genreName) }
	)
	val player = koinInject<MediaPlayerViewModel>()
	val preferenceManager = koinInject<PreferenceManager>()
	val state by viewModel.genreState.collectAsState()

	Scaffold(
		topBar = {
			NestedTopBar({ Text(state.data?.genre?.name ?: genreName) })
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			if (preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = state !is UiState.Loading,
			onRefresh = { viewModel.refreshGenre(fullRefresh = true) },
			key = state
		) {
			when (val current = state) {
				is UiState.Loading -> {
					current.data?.let {
						GenreDetailContent(
							state = it,
							player = player,
							viewModel = viewModel,
							contentPadding = innerPadding.withoutTop()
						)
					}
				}

				is UiState.Success -> GenreDetailContent(
					state = current.data,
					player = player,
					viewModel = viewModel,
					contentPadding = innerPadding.withoutTop()
				)

				is UiState.Error -> {
					current.data?.let {
						GenreDetailContent(
							state = it,
							player = player,
							viewModel = viewModel,
							contentPadding = innerPadding.withoutTop()
						)
					} ?: ContentUnavailable(
						icon = Icons.Outlined.Genre,
						label = stringResource(Res.string.info_no_genres)
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GenreDetailContent(
	state: GenreDetailState,
	player: MediaPlayerViewModel,
	viewModel: GenreDetailViewModel,
	contentPadding: PaddingValues
) {
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val durationLabel = queueTotalDurationLabel(state.totalDuration.inWholeSeconds)

	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = contentPadding,
		verticalArrangement = Arrangement.spacedBy(18.dp)
	) {
		item {
			Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
				Text(
					text = state.genre.name,
					style = MaterialTheme.typography.displaySmallEmphasized
				)
				Spacer(Modifier.height(12.dp))
				Text(
					text = listOf(
						pluralStringResource(Res.plurals.count_artists, state.artists.size, state.artists.size),
						pluralStringResource(Res.plurals.count_albums, state.albums.size, state.albums.size),
						pluralStringResource(Res.plurals.count_songs, state.collection.songs.size, state.collection.songs.size),
						durationLabel
					).joinToString(" • "),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}

		item {
			Row(
				modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Button(
					modifier = Modifier.weight(1f),
					enabled = state.collection.songs.isNotEmpty(),
					onClick = {
						platformContext.clickSound()
						viewModel.play(player)
					}
				) {
					Icon(Icons.Filled.Play, null)
					Spacer(Modifier.width(6.dp))
					Text(stringResource(Res.string.action_play))
				}
				OutlinedButton(
					enabled = state.collection.songs.isNotEmpty(),
					onClick = {
						platformContext.clickSound()
						viewModel.shuffle(player)
					}
				) {
					Icon(Icons.Outlined.Shuffle, stringResource(Res.string.action_shuffle))
				}
				OutlinedButton(
					enabled = state.collection.songs.isNotEmpty(),
					onClick = {
						platformContext.clickSound()
						viewModel.playNext(player)
					}
				) {
					Text(stringResource(Res.string.action_play_next))
				}
				OutlinedButton(
					enabled = state.collection.songs.isNotEmpty(),
					onClick = {
						platformContext.clickSound()
						viewModel.addToQueue(player)
					}
				) {
					Icon(Icons.Outlined.PlaylistAdd, stringResource(Res.string.action_add_to_queue))
				}
			}
		}

		if (state.artists.isNotEmpty()) {
			item {
				SectionTitle(stringResource(Res.string.title_artists))
			}
			item {
				LazyRow(
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					contentPadding = PaddingValues(horizontal = 16.dp)
				) {
					items(state.artists, key = { it.id }) { artist ->
						val artistImageUrl = artistImageUrlForExternalArtworkPolicy(
							artist = artist,
							externalArtworkEnabled = preferenceManager.aurralEnabled
						)
						val artistImageRequestHeaders = aurralRequestHeadersForUrl(
							baseUrl = preferenceManager.aurralBaseUrl,
							imageUrl = artistImageUrl,
							requestHeaders = preferenceManager.aurralRequestHeadersMap()
						)
						ArtGridItem(
							modifier = Modifier.width(150.dp),
							onClick = dropUnlessResumed {
								platformContext.clickSound()
								backStack.add(Screen.ArtistDetail(artist.id))
							},
							coverArtId = artistCoverArtIdForExternalArtworkPolicy(
								artist = artist,
								externalArtworkEnabled = preferenceManager.aurralEnabled
							),
							imageUrl = artistImageUrl,
							imageRequestHeaders = artistImageRequestHeaders,
							title = artist.name,
							subtitle = pluralStringResource(
								Res.plurals.count_albums,
								artist.albumCount,
								artist.albumCount
							),
							fallbackKind = "Artist",
							id = artist.id,
							tab = "genre-${state.genre.name}"
						)
					}
				}
			}
		}

		if (state.albums.isNotEmpty()) {
			item {
				SectionTitle(stringResource(Res.string.title_albums))
			}
			item {
				LazyRow(
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					contentPadding = PaddingValues(horizontal = 16.dp)
				) {
					items(state.albums, key = { it.id }) { album ->
						ArtGridItem(
							modifier = Modifier.width(150.dp),
							onClick = dropUnlessResumed {
								platformContext.clickSound()
								backStack.add(Screen.CollectionDetail(album.id, "Genre"))
							},
							coverArtId = album.coverArtId,
							title = album.name,
							subtitle = album.artistName,
							fallbackKind = "Album",
							id = album.id,
							tab = "genre-${state.genre.name}"
						)
					}
				}
			}
		}
	}
}

@Composable
private fun SectionTitle(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.titleMediumEmphasized,
		modifier = Modifier.padding(horizontal = 24.dp)
	)
}
