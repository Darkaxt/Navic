package paige.navic.ui.screens.collection.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_unknown_genre
import navic.composeapp.generated.resources.info_unknown_year
import navic.composeapp.generated.resources.subtitle_playlist
import navic.composeapp.generated.resources.title_genres
import org.jetbrains.compose.resources.stringResource
import paige.navic.LocalPlatformContext
import paige.navic.LocalNavStack
import paige.navic.LocalSharedTransitionScope
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainGenreCollection
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.displayName
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.screens.artist.rememberArtistCreditDestinationResolver
import paige.navic.ui.theme.defaultFont
import paige.navic.util.ui.EmphasizedDecelerateEasing

@Composable
fun CollectionDetailScreenHeadingRow(
	collection: DomainSongCollection,
	tab: String,
	titleAlpha: Float
) {
	val platformContext = LocalPlatformContext.current
	val backStack = LocalNavStack.current
	val scope = rememberCoroutineScope()
	val resolveArtistCreditDestination = rememberArtistCreditDestinationResolver()
	val displayName = collection.displayName()
	with(LocalSharedTransitionScope.current) {
		CoverArt(
			coverArtId = collection.coverArtId,
			contentDescription = displayName,
			fallbackKind = when (collection) {
				is DomainAlbum -> "Album"
				is DomainGenreCollection -> "Genre"
				is DomainPlaylist -> "Playlist"
			},
			modifier = Modifier
				.widthIn(0.dp, 420.dp)
				.padding(horizontal = 64.dp)
				.aspectRatio(1f)
				.sharedElement(
					sharedContentState = this@with.rememberSharedContentState("${tab}-${collection.id}-cover"),
					boundsTransform = BoundsTransform { _, _ ->
						tween(
							durationMillis = 500,
							easing = EmphasizedDecelerateEasing
						)
					},
					animatedVisibilityScope = LocalNavAnimatedContentScope.current
				)
				.alpha(titleAlpha),
			crossfadeMs = 0
		)
		Column(
			modifier = Modifier
				.padding(horizontal = 31.dp)
				.padding(top = 10.dp, bottom = 8.dp)
				.alpha(titleAlpha),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Text(
				displayName,
				style = MaterialTheme.typography.headlineSmall,
				textAlign = TextAlign.Center,
				modifier = Modifier
			)
			val subtitle = when (collection) {
				is DomainAlbum -> collection.artistName
				is DomainGenreCollection -> null
				is DomainPlaylist -> collection.comment
			}
			subtitle?.let { subtitle ->
				Text(
					subtitle,
					color = MaterialTheme.colorScheme.primary,
					modifier = Modifier.clickable(collection is DomainAlbum, onClick = dropUnlessResumed {
						platformContext.clickSound()
						(collection as? DomainAlbum)?.let { album ->
							scope.launch {
								resolveArtistCreditDestination(
									album.artistId,
									album.artistName,
									true
								)?.let(backStack::add)
							}
						}
					}),
					style = MaterialTheme.typography.bodyMedium,
					fontFamily = defaultFont(grade = 100, round = 100f)
				)
			}
			Text(
				when (collection) {
					is DomainAlbum -> "${collection.genre ?: stringResource(Res.string.info_unknown_genre)} • ${
						collection.year ?: stringResource(
							Res.string.info_unknown_year
						)
					}"
					is DomainGenreCollection -> stringResource(Res.string.title_genres)
					is DomainPlaylist -> stringResource(Res.string.subtitle_playlist)
				},
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				style = MaterialTheme.typography.bodySmall,
				fontFamily = defaultFont(grade = 100, round = 100f)
			)
		}
	}
}
