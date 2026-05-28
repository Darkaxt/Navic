package paige.navic.ui.screens.nowPlaying.components.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_up_next
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.nowPlayingUpNextItems
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.navigation.Screen

@Composable
fun NowPlayingUpNextRow() {
	val preferenceManager = koinInject<PreferenceManager>()
	if (!preferenceManager.showNowPlayingUpNext) return

	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val upNextSongs = nowPlayingUpNextItems(
		queue = playerState.queue,
		currentIndex = playerState.currentIndex,
		maxCount = preferenceManager.nowPlayingUpNextCount
	)
	if (upNextSongs.isEmpty()) return

	val backStack = LocalNavStack.current
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp)
			.padding(top = 8.dp)
			.clickable(onClick = dropUnlessResumed { backStack.add(Screen.Queue) })
	) {
		Text(
			text = stringResource(Res.string.title_up_next),
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		upNextSongs.forEach { song ->
			Text(
				text = "${song.title} - ${song.artistName}",
				style = MaterialTheme.typography.bodyMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}
