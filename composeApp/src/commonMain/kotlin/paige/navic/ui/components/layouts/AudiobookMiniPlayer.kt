package paige.navic.ui.components.layouts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.kyant.capsule.ContinuousRoundedRectangle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_play
import navic.composeapp.generated.resources.info_not_playing
import navic.composeapp.generated.resources.title_audiobooks
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.settings.MiniPlayerProgressStyle
import paige.navic.domain.models.settings.MiniPlayerStyle
import paige.navic.icons.Icons
import paige.navic.icons.filled.Pause
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.Audiobooks
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.shared.AudiobookPlaybackManager
import paige.navic.ui.components.common.MarqueeText
import paige.navic.ui.core.AudiobookMiniPlayerUiState

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AudiobookMiniPlayer(
	state: AudiobookMiniPlayerUiState,
	modifier: Modifier = Modifier,
	enabled: Boolean = true
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val playbackManager = koinInject<AudiobookPlaybackManager>()
	val backStack = LocalNavStack.current
	val navBarPadding = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
	val detached = preferenceManager.miniPlayerStyle == MiniPlayerStyle.Detached
	val outerPadding = if (detached) 12.dp else 0.dp
	val shape = ContinuousRoundedRectangle(if (detached) 16.dp else 0.dp)
	val iconSize = if (detached) 24.dp else 32.dp
	val isInteractive = enabled && state.isAvailable
	val destination = state.bookId?.let { bookId ->
		state.versionRowId?.let { versionRowId ->
			paige.navic.ui.navigation.Screen.BinderyAudiobookPlayer(
				bookId = bookId,
				title = state.bookTitle ?: bookId,
				versionRowId = versionRowId
			)
		}
	}
	val title = state.bookTitle ?: stringResource(Res.string.title_audiobooks)
	val subtitle = state.chapterLabel
		?: state.sectionLabel
		?: state.narratorLabel
		?: stringResource(Res.string.info_not_playing)

	val openPlayer = dropUnlessResumed {
		if (destination != null && backStack.lastOrNull() != destination) {
			platformContext.clickSound()
			backStack.add(destination)
		}
	}

	Box(
		modifier = modifier
			.widthIn(max = if (detached) 600.dp else Dp.Unspecified)
			.padding(
				bottom = if (detached) outerPadding + navBarPadding else 0.dp,
				start = outerPadding,
				end = outerPadding
			),
		contentAlignment = Alignment.Center
	) {
		ListItem(
			modifier = Modifier.dropShadow(
				shape,
				Shadow(
					radius = if (detached) 10.dp else 8.dp,
					alpha = 0.25f
				)
			),
			contentPadding = PaddingValues(
				start = if (detached) 10.dp else 16.dp,
				end = if (detached) 10.dp else 16.dp,
				top = if (detached) 10.dp else 16.dp,
				bottom = (if (detached) 10.dp else 12.dp) + if (detached) 0.dp else navBarPadding
			),
			verticalAlignment = Alignment.CenterVertically,
			colors = ListItemDefaults.colors(containerColor = NavigationBarDefaults.containerColor),
			shapes = ListItemDefaults.shapes(
				shape = shape,
				selectedShape = shape,
				pressedShape = shape,
				focusedShape = shape,
				hoveredShape = shape,
				draggedShape = shape
			),
			onClick = openPlayer,
			leadingContent = {
				Box(
					modifier = Modifier
						.size(if (detached) 48.dp else 50.dp)
						.clip(ContinuousRoundedRectangle(8.dp))
						.background(MaterialTheme.colorScheme.secondaryContainer),
					contentAlignment = Alignment.Center
				) {
					Icon(
						imageVector = Icons.Outlined.Audiobooks,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSecondaryContainer,
						modifier = Modifier.size(28.dp)
					)
				}
			},
			trailingContent = {
				Row(horizontalArrangement = Arrangement.spacedBy(if (detached) 8.dp else 12.dp)) {
					IconButton(
						onClick = {
							platformContext.clickSound()
							playbackManager.dispatch(
								if (state.isPlaying) {
									ReaderReadaloudPlaybackCommand.Pause
								} else {
									ReaderReadaloudPlaybackCommand.Play
								}
							)
						},
						enabled = isInteractive,
						colors = IconButtonDefaults.iconButtonVibrantColors()
					) {
						Icon(
							imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.Play,
							contentDescription = stringResource(Res.string.action_play),
							modifier = Modifier.size(iconSize)
						)
					}
				}
			},
			content = { MarqueeText(title) },
			supportingContent = { MarqueeText(subtitle) },
			enabled = isInteractive
		)
		if (preferenceManager.miniPlayerProgressStyle == MiniPlayerProgressStyle.Visible
			|| preferenceManager.miniPlayerProgressStyle == MiniPlayerProgressStyle.Seekable
		) {
			val progress = animateFloatAsState(state.progress.coerceIn(0f, 1f))
			val alignment = if (detached) Alignment.BottomStart else Alignment.TopStart
			Box(
				modifier = Modifier
					.matchParentSize()
					.clip(shape)
					.align(alignment),
				contentAlignment = alignment
			) {
				if (!detached) {
					Box(
						Modifier
							.background(MaterialTheme.colorScheme.surfaceBright)
							.fillMaxWidth()
							.height(3.dp)
					)
				}
				Box(
					Modifier
						.background(MaterialTheme.colorScheme.primary.copy(alpha = .7f))
						.fillMaxWidth(progress.value)
						.height(3.dp)
				)
			}
		}
	}
}
