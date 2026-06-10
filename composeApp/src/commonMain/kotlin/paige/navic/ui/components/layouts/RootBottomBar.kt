package paige.navic.ui.components.layouts

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.settings.BottomBarCollapseMode
import paige.navic.domain.models.settings.MiniPlayerStyle
import paige.navic.shared.AudiobookPlaybackManager
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.navigation.Screen
import paige.navic.util.ui.easedVerticalGradient

@Composable
fun RootBottomBar(
	scrolled: Boolean,
	modifier: Modifier = Modifier,
	shadows: Boolean = true,
	hideMiniPlayer: Boolean = false,
	bottomBarWindowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	val screen = backStack.lastOrNull() as? Screen
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val audiobookPlayer = koinInject<AudiobookPlaybackManager>()
	val audiobookState by audiobookPlayer.uiState.collectAsState()
	val playbackKind = rootMiniPlayerPlaybackKind(
		screen = screen,
		hasMusicPlayback = playerState.currentSong != null,
		audiobookAvailable = audiobookState.isAvailable,
		audiobookPlaying = audiobookState.isPlaying,
		binderyEnabled = preferenceManager.binderyEnabled
	)
	val shouldShowMiniPlayer = !hideMiniPlayer && playbackKind != MiniPlayerPlaybackKind.None
	val scrolled =
		scrolled && preferenceManager.bottomBarCollapseMode == BottomBarCollapseMode.OnScroll
	val progress by animateFloatAsState(
		targetValue = if (scrolled) 0f else 1f,
		animationSpec = spring(
			dampingRatio = Spring.DampingRatioLowBouncy,
			stiffness = Spring.StiffnessMediumLow
		)
	)
	val shadowFadeProgress by animateFloatAsState(
		targetValue = if (scrolled || !shadows) 0f else 1f,
		animationSpec = tween(durationMillis = 600)
	)
	Column(
		modifier = modifier.then(
			if (preferenceManager.miniPlayerStyle == MiniPlayerStyle.Detached)
				Modifier.background(
					Brush.easedVerticalGradient(color = MaterialTheme.colorScheme.surface.copy(alpha = shadowFadeProgress))
				)
			else Modifier
		)
	) {
		val miniPlayerModifier = Modifier.graphicsLayer {
				alpha = progress.coerceIn(0f..1f)
				translationY = ((1f - progress) * (size.height * 2)).coerceAtLeast(
					if (preferenceManager.miniPlayerStyle == MiniPlayerStyle.Detached) -2048f else 0f
				)
			}
		if (shouldShowMiniPlayer) {
			when (playbackKind) {
				MiniPlayerPlaybackKind.Music -> MiniPlayer(
					modifier = miniPlayerModifier,
					enabled = !scrolled
				)
				MiniPlayerPlaybackKind.Audiobook -> AudiobookMiniPlayer(
					state = audiobookState,
					modifier = miniPlayerModifier,
					enabled = !scrolled
				)
				MiniPlayerPlaybackKind.None -> Unit
			}
		}
		BottomBar(
			containerColor = if (preferenceManager.miniPlayerStyle == MiniPlayerStyle.Detached)
				NavigationBarDefaults.containerColor.copy(alpha = 0f)
			else NavigationBarDefaults.containerColor,
			windowInsets = bottomBarWindowInsets,
			modifier = Modifier.graphicsLayer {
				alpha = progress.coerceIn(0f..1f)
				translationY = ((1f - progress) * size.height).coerceAtLeast(
					if (preferenceManager.miniPlayerStyle == MiniPlayerStyle.Detached) -2048f else 0f
				)
			},
			enabled = !scrolled
		)
	}
}
