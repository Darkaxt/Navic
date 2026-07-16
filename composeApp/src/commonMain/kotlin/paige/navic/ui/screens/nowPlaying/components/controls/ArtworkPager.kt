package paige.navic.ui.screens.nowPlaying.components.controls

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.NowPlayingPagerIntentTracker
import paige.navic.domain.models.shouldEnableNowPlayingArtworkSwipe
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.screens.nowPlaying.components.NowPlayingArtwork

@Composable
fun NowPlayingArtworkPager(
	modifier: Modifier = Modifier,
	isLandscape: Boolean,
	isWideLandscape: Boolean = false,
	onArtworkTap: (() -> Unit)? = null
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()

	val pagerState = rememberPagerState(
		initialPage = playerState.currentIndex.coerceAtLeast(0),
		pageCount = { playerState.queue.size }
	)
	val tracker = remember { NowPlayingPagerIntentTracker() }
	val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
	val currentIndex by rememberUpdatedState(playerState.currentIndex)
	val queueSize by rememberUpdatedState(playerState.queue.size)
	val isPaused by rememberUpdatedState(playerState.isPaused)

	val visible = true
	val scale by animateFloatAsState(if (visible) 1f else 0f)
	val offset by animateDpAsState(if (visible) 0.dp else 200.dp)

	LaunchedEffect(playerState.currentIndex, isDragged) {
		if (
			!isDragged &&
			playerState.currentIndex != -1 &&
			playerState.currentIndex != pagerState.currentPage
		) {
			pagerState.animateScrollToPage(playerState.currentIndex)
		}
	}

	LaunchedEffect(isDragged) {
		if (isDragged) tracker.onUserDragStarted()
	}

	LaunchedEffect(pagerState) {
		snapshotFlow { pagerState.settledPage }.collect { page ->
			tracker.onSettledPage(
				settledPage = page,
				currentIndex = currentIndex,
				queueSize = queueSize,
				isPaused = isPaused
			)?.let { request ->
				player.selectQueueItem(
					index = request.index,
					playWhenReady = request.playWhenReady,
					origin = request.origin
				)
			}
		}
	}

	HorizontalPager(
		modifier = modifier.scale(scale).offset {
			IntOffset(x = 0, y = offset.roundToPx())
		},
		state = pagerState,
		contentPadding = PaddingValues(horizontal = if (isLandscape) 0.dp else 8.dp),
		userScrollEnabled = shouldEnableNowPlayingArtworkSwipe(
			swipeToSkip = preferenceManager.swipeToSkip,
			artworkSwipeToSkip = preferenceManager.nowPlayingArtworkSwipeToSkip
		),
		overscrollEffect = null
	) { page ->
		val song = playerState.queue[page]
		Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center
		) {
			NowPlayingArtwork(
				song = song,
				isLandscape = isLandscape,
				isWideLandscape = isWideLandscape,
				onClick = onArtworkTap
			)
		}
	}
}
