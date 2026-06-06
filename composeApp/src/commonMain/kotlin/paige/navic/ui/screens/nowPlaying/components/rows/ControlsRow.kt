package paige.navic.ui.screens.nowPlaying.components.rows

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.NowPlayingControlsLayoutBlock
import paige.navic.domain.models.nowPlayingControlsLayoutBlocks
import paige.navic.domain.models.shouldOpenQueueFromNowPlayingControlsTap
import paige.navic.domain.models.shouldOpenQueueFromNowPlayingControlsSwipeUp
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.nowPlaying.components.controls.NowPlayingProgressBar
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun NowPlayingControlsRow(
	modifier: Modifier = Modifier,
	isLandscape: Boolean,
	hasCurrentSong: Boolean,
	showTechnicalInfo: Boolean,
	onCollapse: (() -> Unit)?,
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit,
	songRating: Int,
	onSetSongRating: (Int) -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	var visible by rememberSaveable { mutableStateOf(false) }
	val scale by animateFloatAsState(if (visible) 1f else 0f)
	val offset by animateDpAsState(if (visible) 0.dp else 200.dp)
	val openQueueOnSwipeUp = preferenceManager.openQueueOnNowPlayingControlsSwipeUp
	val openQueueOnTap = shouldOpenQueueFromNowPlayingControlsTap(
		enabled = preferenceManager.openQueueOnNowPlayingControlsTap,
		hasCurrentSong = hasCurrentSong
	)

	fun openQueue() {
		if (backStack.lastOrNull() !is Screen.Queue) {
			platformContext.clickSound()
			backStack.add(Screen.Queue)
		}
	}

	LaunchedEffect(Unit) {
		delay(200.milliseconds)
		visible = true
	}

	Column(
		modifier = modifier
			.scale(scale)
			.offset {
				IntOffset(x = 0, y = offset.roundToPx())
			}
			.then(
				if (!openQueueOnSwipeUp || !hasCurrentSong) {
					Modifier
				} else {
					Modifier.pointerInput(openQueueOnSwipeUp, hasCurrentSong) {
						var accumulatedVerticalDrag = 0f
						var openedQueue = false

						detectVerticalDragGestures(
							onDragStart = {
								accumulatedVerticalDrag = 0f
								openedQueue = false
							},
							onVerticalDrag = { _, dragAmount ->
								if (openedQueue) return@detectVerticalDragGestures

								accumulatedVerticalDrag += dragAmount
								if (shouldOpenQueueFromNowPlayingControlsSwipeUp(
										enabled = openQueueOnSwipeUp,
										hasCurrentSong = hasCurrentSong,
										accumulatedVerticalDragPx = accumulatedVerticalDrag
									)
								) {
									openedQueue = true
									openQueue()
								}
							},
							onDragEnd = {
								accumulatedVerticalDrag = 0f
								openedQueue = false
							},
							onDragCancel = {
								accumulatedVerticalDrag = 0f
								openedQueue = false
							}
						)
					}
				}
			),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		NowPlayingInfoRow(
			onCollapse = onCollapse,
			songIsStarred = songIsStarred,
			onSetSongIsStarred = onSetSongIsStarred,
			songRating = songRating,
			onSetSongRating = onSetSongRating
		)
		nowPlayingControlsLayoutBlocks(
			swapControlsAndTimeline = preferenceManager.swapNowPlayingControlsAndTimeline,
			showTechnicalInfo = showTechnicalInfo
		)
			.forEachIndexed { index, block ->
				if (index > 0) {
					Spacer(modifier = Modifier.height(if (isLandscape) 24.dp else 30.dp))
				}
				when (block) {
					NowPlayingControlsLayoutBlock.Timeline -> NowPlayingTimelineBlock()

					NowPlayingControlsLayoutBlock.TechnicalInfo -> NowPlayingTechnicalInfoRow()

					NowPlayingControlsLayoutBlock.PlaybackButtons -> NowPlayingButtonsRow(
						modifier = if (openQueueOnTap) {
							Modifier.clickable {
								openQueue()
							}
						} else {
							Modifier
						}
					)
				}
			}
	}
}

@Composable
private fun NowPlayingTimelineBlock() {
	Column {
		NowPlayingProgressBar()
		NowPlayingDurationsRow()
		NowPlayingUpNextRow()
	}
}
