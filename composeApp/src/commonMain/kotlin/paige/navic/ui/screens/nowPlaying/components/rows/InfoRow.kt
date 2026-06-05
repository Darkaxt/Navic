package paige.navic.ui.screens.nowPlaying.components.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_not_playing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.nowPlayingInfoSubtitle
import paige.navic.domain.models.shouldShowNowPlayingInfoIcon
import paige.navic.domain.models.shouldShowNowPlayingMoreAction
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.MarqueeText
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.artist.rememberArtistCreditDestinationResolver
import paige.navic.ui.screens.nowPlaying.components.controls.NowPlayingMoreButton
import paige.navic.ui.screens.nowPlaying.components.controls.NowPlayingStarButton
import paige.navic.util.core.InlineExplicitIconLarge
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Album
import paige.navic.icons.outlined.Artist

@Composable
fun NowPlayingInfoRow(
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit,
	songRating: Int,
	onSetSongRating: (Int) -> Unit
) {
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val scope = rememberCoroutineScope()
	val resolveArtistCreditDestination = rememberArtistCreditDestinationResolver()
	val playerState by player.uiState.collectAsState()
	val song = playerState.currentSong
	val showAlbumIcon = shouldShowNowPlayingInfoIcon(
		enabled = preferenceManager.showNowPlayingInfoIcons,
		hasNavigationTarget = song?.albumId != null
	)
	val showArtistIcon = shouldShowNowPlayingInfoIcon(
		enabled = preferenceManager.showNowPlayingInfoIcons,
		hasNavigationTarget = song?.artistId != null
	)
	val subtitle = song?.let { currentSong ->
		nowPlayingInfoSubtitle(
			style = preferenceManager.nowPlayingInfoStyle,
			albumTitle = currentSong.albumTitle,
			artistName = currentSong.artistName
		)
	} ?: stringResource(Res.string.info_not_playing)
	Row(
		modifier = Modifier
			.padding(horizontal = 16.dp)
			.padding(bottom = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Column(Modifier.weight(1f)) {
			song?.let { song ->
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					modifier = Modifier.clickable(onClick = dropUnlessResumed {
						song.albumId?.let { albumId ->
							backStack.removeLastOrNull()

							val lastScreen = backStack.lastOrNull()

							val isSameAlbum = if (lastScreen is Screen.CollectionDetail) {
								lastScreen.collectionId == song.albumId
							} else {
								false
							}

							if (!isSameAlbum)
								backStack.add(
									Screen.CollectionDetail(
										albumId,
										""
									)
								)
						}
					})
				) {
					if (showAlbumIcon) {
						Icon(
							imageVector = Icons.Outlined.Album,
							contentDescription = null,
							modifier = Modifier.size(18.dp),
							tint = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
					MarqueeText(
						text = buildAnnotatedString {
							append(song.title)
							if (song.explicitStatus == DomainExplicitStatus.Explicit) {
								append(" ")
								appendInlineContent("InlineExplicitIcon")
							}
						},
						inlineContent = InlineExplicitIconLarge,
						modifier = Modifier.weight(1f),
						style = MaterialTheme.typography.bodyLarge
							.copy(
								fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.1
							),
					)
				}
			}
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				modifier = Modifier.clickable(
					song != null,
					onClick = dropUnlessResumed {
						song?.let { currentSong ->
							scope.launch {
								resolveArtistCreditDestination(
									currentSong.artistId,
									currentSong.artistName,
									false
								)?.let { destination ->
									backStack.remove(Screen.NowPlaying)
									backStack.add(destination)
								}
							}
						}
					}
				),
			) {
				if (showArtistIcon) {
					Icon(
						imageVector = Icons.Outlined.Artist,
						contentDescription = null,
						modifier = Modifier.size(16.dp),
						tint = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				MarqueeText(
					modifier = Modifier.weight(1f),
					style = MaterialTheme.typography.bodyMedium
						.copy(
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							fontSize = MaterialTheme.typography.bodyMedium.fontSize * 1.1
						),
					text = subtitle
				)
			}
		}
		Row(
			horizontalArrangement = Arrangement.spacedBy(10.dp)
		) {
			NowPlayingStarButton(
				songIsStarred = songIsStarred,
				onSetSongIsStarred = onSetSongIsStarred
			)
			if (shouldShowNowPlayingMoreAction(preferenceManager.showNowPlayingMoreAction)) {
				NowPlayingMoreButton(
					songRating = songRating,
					onSetSongRating = onSetSongRating
				)
			}
		}
	}
}
