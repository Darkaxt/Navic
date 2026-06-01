package paige.navic.ui.screens.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_songs
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongListType
import paige.navic.domain.models.QueueDuplicateAction
import paige.navic.domain.models.duplicateQueueActionFor
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.dialogs.QueueDuplicateDialog
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.share.dialogs.ShareDialog
import paige.navic.ui.screens.song.components.SongListScreenSortButton
import paige.navic.ui.screens.song.components.songListScreenContent
import paige.navic.ui.screens.song.viewmodels.SongListViewModel
import paige.navic.util.ui.withoutTop
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongListScreen(
	nested: Boolean,
	artistId: String? = null,
	artistName: String? = null,
	listType: DomainSongListType
) {
	val viewModel = koinViewModel<SongListViewModel>(
		key = artistId,
		parameters = { parametersOf(listType, artistId) }
	)
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val songsState by viewModel.songsState.collectAsStateWithLifecycle()
	val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSorting by viewModel.selectedSorting.collectAsStateWithLifecycle()
	val selectedReversed by viewModel.selectedReversed.collectAsStateWithLifecycle()
	val starred by viewModel.starred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()
	val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
	val playlistSongIds by viewModel.playlistSongIds.collectAsStateWithLifecycle()
	val songListIntegrationIndicators = integrationLoadingIndicators()

	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var songToQueue by remember { mutableStateOf<DomainSong?>(null) }
	var queueDuplicateAction by remember { mutableStateOf<QueueDuplicateAction?>(null) }
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	fun queueSongOrConfirmDuplicate(
		song: DomainSong,
		action: QueueDuplicateAction,
		onQueue: () -> Unit
	) {
		val duplicateAction = duplicateQueueActionFor(
			queueSongIds = player.uiState.value.queue.map { it.id },
			songId = song.id,
			action = action
		)
		if (duplicateAction != null) {
			songToQueue = song
			queueDuplicateAction = duplicateAction
		} else {
			onQueue()
		}
	}

	val actions: @Composable RowScope.() -> Unit = {
		SongListScreenSortButton(
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
					title = { Text(artistName ?: stringResource(Res.string.title_songs)) },
					scrollBehavior = scrollBehavior,
					actions = actions
				)
			} else {
				NestedTopBar(
					title = { Text(artistName ?: stringResource(Res.string.title_songs)) },
					actions = actions
				)
			}
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			if (!nested || preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		Box(Modifier.fillMaxSize()) {
			PullToRefreshBox(
				modifier = Modifier
					.padding(top = innerPadding.calculateTopPadding())
					.background(MaterialTheme.colorScheme.surface),
				finished = songsState !is UiState.Loading,
				onRefresh = { viewModel.refreshSongs(true) },
				key = songsState
			) {
				LazyColumn(
					modifier = if (!nested)
						Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
					else Modifier.fillMaxSize(),
					contentPadding = innerPadding.withoutTop(),
					verticalArrangement = if ((songsState as? UiState.Success)?.data?.isEmpty() == true)
						Arrangement.Center
					else Arrangement.spacedBy(12.dp)
				) {
					songListScreenContent(
						state = songsState,
						selectedSongIsStarred = starred,
						selectedSongRating = selectedSongRating,
						selectedSong = selectedSong,
						onUpdateSelection = { viewModel.selectSong(it) },
						onClearSelection = { viewModel.clearSelection() },
						onSetShareId = { newShareId ->
							shareId = newShareId
						},
						onSetStarred = { viewModel.starSong(it) },
						onStartSongRadio = { song ->
							player.startSongRadio(song)
						},
						onPlayNext = { song ->
							queueSongOrConfirmDuplicate(song, QueueDuplicateAction.PlayNext) {
								player.playNextSingle(song)
							}
						},
						onAddToQueue = { song ->
							queueSongOrConfirmDuplicate(song, QueueDuplicateAction.AddToQueue) {
								player.addToQueueSingle(song)
							}
						},
						onPlaySong = { song ->
							player.clearQueue()
							player.addToQueueSingle(song)
							player.playAt(0)
						},
						onSetRating = { viewModel.rateSelectedSong(it) },
						onDownload = { viewModel.downloadSong(it) },
						allDownloads = allDownloads,
						onCancelDownload = { viewModel.cancelDownload(it.id) },
						onDeleteDownload = { viewModel.deleteDownload(it.id) },
						playlistSongIds = playlistSongIds
					)
				}
			}
			IntegrationLoadingIndicatorStrip(
				indicators = songListIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = songListIntegrationIndicators
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = innerPadding.calculateTopPadding() + 8.dp)
			)
		}
	}

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	if (songToQueue != null) {
		QueueDuplicateDialog(
			onDismissRequest = {
				songToQueue = null
				queueDuplicateAction = null
			},
			onConfirm = {
				songToQueue?.let { song ->
					when (queueDuplicateAction) {
						QueueDuplicateAction.PlayNext -> player.playNextSingle(song)
						QueueDuplicateAction.AddToQueue,
						null -> player.addToQueueSingle(song)
					}
				}
			}
		)
	}
}
