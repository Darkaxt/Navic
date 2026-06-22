package paige.navic.ui.screens.nowPlaying.components.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_up_next
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.nowPlayingUpNextItems
import paige.navic.domain.models.shouldShowNowPlayingUpNextArtwork
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.CoverArtNormalization
import paige.navic.ui.components.common.PlaybackSongCoverArt
import paige.navic.ui.navigation.Screen

internal fun nowPlayingUpNextItemWidth(showArtwork: Boolean): Dp =
	if (showArtwork) 188.dp else 220.dp

internal enum class NowPlayingUpNextContainerTone {
	SecondaryContainer
}

internal fun nowPlayingUpNextContainerTone(): NowPlayingUpNextContainerTone =
	NowPlayingUpNextContainerTone.SecondaryContainer

internal fun nowPlayingUpNextItemContainerAlpha(): Float = 0.86f

internal fun nowPlayingUpNextBottomPadding(showTechnicalInfo: Boolean): Dp =
	0.dp

@Composable
fun NowPlayingUpNextRow(showTechnicalInfoBelow: Boolean = false) {
	val preferenceManager = koinInject<PreferenceManager>()
	val showNowPlayingUpNext = preferenceManager.showNowPlayingUpNext
	if (!showNowPlayingUpNext) return
	val showArtwork = shouldShowNowPlayingUpNextArtwork(
		showNowPlayingUpNext = showNowPlayingUpNext,
		showNowPlayingUpNextArtwork = preferenceManager.showNowPlayingUpNextArtwork
	)

	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val upNextSongs = nowPlayingUpNextItems(
		queue = playerState.queue,
		currentIndex = playerState.currentIndex,
		maxCount = preferenceManager.nowPlayingUpNextCount,
		repeatMode = playerState.repeatMode,
		upcomingIndexes = playerState.upcomingIndexes
	)
	if (upNextSongs.isEmpty()) return

	val backStack = LocalNavStack.current
	val onOpenQueue = dropUnlessResumed { backStack.add(Screen.Queue) }
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp)
			.padding(
				top = 8.dp,
				bottom = nowPlayingUpNextBottomPadding(showTechnicalInfoBelow)
			)
	) {
		Text(
			text = stringResource(Res.string.title_up_next),
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.clickable(onClick = onOpenQueue)
		)
		LazyRow(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			contentPadding = PaddingValues(end = 16.dp)
		) {
			items(
				items = upNextSongs,
				key = { song -> song.id }
			) { song ->
				NowPlayingUpNextItem(
					song = song,
					showArtwork = showArtwork,
					onClick = onOpenQueue
				)
			}
		}
	}
}

@Composable
private fun NowPlayingUpNextItem(
	song: DomainSong,
	showArtwork: Boolean,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		shape = MaterialTheme.shapes.small,
		color = when (nowPlayingUpNextContainerTone()) {
			NowPlayingUpNextContainerTone.SecondaryContainer ->
				MaterialTheme.colorScheme.secondaryContainer.copy(alpha = nowPlayingUpNextItemContainerAlpha())
		},
		contentColor = when (nowPlayingUpNextContainerTone()) {
			NowPlayingUpNextContainerTone.SecondaryContainer -> MaterialTheme.colorScheme.onSecondaryContainer
		},
		modifier = Modifier
			.width(nowPlayingUpNextItemWidth(showArtwork))
			.heightIn(min = if (showArtwork) 52.dp else 42.dp)
	) {
		if (showArtwork) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.padding(8.dp)
			) {
				PlaybackSongCoverArt(
					song = song,
					contentDescription = null,
					modifier = Modifier.size(36.dp),
					normalization = CoverArtNormalization.TrimWhitespace,
				)
				Column(
					modifier = Modifier
						.padding(start = 10.dp)
						.weight(1f)
				) {
					Text(
						text = song.title,
						style = MaterialTheme.typography.bodyMedium,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					Text(
						text = song.artistName,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
		} else {
			Column(
				modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
			) {
				Text(
					text = song.title,
					style = MaterialTheme.typography.bodyMedium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Text(
					text = song.artistName,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
		}
	}
}
