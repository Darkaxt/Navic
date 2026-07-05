package paige.navic.ui.screens.nowPlaying.components.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_not_playing
import navic.composeapp.generated.resources.action_navigate_back
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
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
import paige.navic.icons.outlined.KeyboardArrowDown

@Composable
fun NowPlayingInfoRow(
	onCollapse: (() -> Unit)? = null,
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit,
	songRating: Int,
	onSetSongRating: (Int) -> Unit,
	showActions: Boolean = true,
	centerText: Boolean = false
) {
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val scope = rememberCoroutineScope()
	val resolveArtistCreditDestination = rememberArtistCreditDestinationResolver()
	val playerState by player.uiState.collectAsState()
	val song = playerState.currentSong
	val showAlbumIcon = shouldShowNowPlayingInfoIcon(
		enabled = preferenceManager.showNowPlayingInfoIcons && !centerText,
		hasNavigationTarget = song?.albumId != null
	)
	val showArtistIcon = shouldShowNowPlayingInfoIcon(
		enabled = preferenceManager.showNowPlayingInfoIcons && !centerText,
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
			.fillMaxWidth()
			.padding(horizontal = 16.dp)
			.padding(bottom = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Column(
			modifier = Modifier.weight(1f),
			horizontalAlignment = if (centerText) Alignment.CenterHorizontally else Alignment.Start
		) {
			song?.let { song ->
				val title = buildAnnotatedString {
					append(song.title)
					if (song.explicitStatus == DomainExplicitStatus.Explicit) {
						append(" ")
						appendInlineContent("InlineExplicitIcon")
					}
				}
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					modifier = Modifier
						.fillMaxWidth()
						.clickable(onClick = dropUnlessResumed {
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
					if (centerText) {
						Text(
							text = title,
							inlineContent = InlineExplicitIconLarge,
							modifier = Modifier.fillMaxWidth(),
							style = MaterialTheme.typography.bodyLarge.copy(
								fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.1,
								textAlign = TextAlign.Center
							),
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					} else {
						MarqueeText(
							text = title,
							inlineContent = InlineExplicitIconLarge,
							modifier = Modifier.weight(1f),
							style = MaterialTheme.typography.bodyLarge
								.copy(
									fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.1
								),
						)
					}
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
				if (centerText) {
					Text(
						modifier = Modifier.fillMaxWidth(),
						style = MaterialTheme.typography.bodyMedium.copy(
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							fontSize = MaterialTheme.typography.bodyMedium.fontSize * 1.1,
							textAlign = TextAlign.Center
						),
						text = subtitle,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				} else {
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
		}
		if (showActions) {
			NowPlayingWindowActions(
				onCollapse = onCollapse,
				songIsStarred = songIsStarred,
				onSetSongIsStarred = onSetSongIsStarred,
				songRating = songRating,
				onSetSongRating = onSetSongRating
			)
		}
	}
}

@Composable
fun NowPlayingWindowActions(
	onCollapse: (() -> Unit)? = null,
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit,
	songRating: Int,
	onSetSongRating: (Int) -> Unit,
	modifier: Modifier = Modifier
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	Row(
		modifier = modifier,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		if (onCollapse != null) {
			IconButton(
				onClick = {
					platformContext.clickSound()
					onCollapse()
				},
				colors = IconButtonDefaults.filledTonalIconButtonColors(),
				modifier = Modifier.size(32.dp)
			) {
				Icon(
					imageVector = Icons.Outlined.KeyboardArrowDown,
					contentDescription = stringResource(Res.string.action_navigate_back)
				)
			}
		}
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
