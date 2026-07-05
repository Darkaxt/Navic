package paige.navic.ui.screens.nowPlaying.components.rows

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.NowPlayingControlsLayoutBlock
import paige.navic.domain.models.nowPlayingControlsLayoutBlocks
import paige.navic.domain.models.shouldOpenQueueFromNowPlayingControlsTap
import paige.navic.domain.models.shouldOpenQueueFromNowPlayingControlsSwipeUp
import paige.navic.domain.models.shouldOverlayTechnicalInfoBetween
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.nowPlaying.components.controls.NowPlayingProgressBar

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
	onSetSongRating: (Int) -> Unit,
	showInlineActions: Boolean = true,
	upNextLayout: paige.navic.domain.models.NowPlayingUpNextLayout =
		paige.navic.domain.models.NowPlayingUpNextLayout.HorizontalRow
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val visible = true
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
			onSetSongRating = onSetSongRating,
			showActions = showInlineActions,
			centerText = !showInlineActions
		)
		val blocks = nowPlayingControlsLayoutBlocks(
			swapControlsAndTimeline = preferenceManager.swapNowPlayingControlsAndTimeline,
			showTechnicalInfo = showTechnicalInfo
		)
		blocks.forEachIndexed { index, block ->
			when (block) {
				NowPlayingControlsLayoutBlock.Timeline -> NowPlayingTimelineBlock(
					upNextLayout = upNextLayout
				)

				NowPlayingControlsLayoutBlock.TechnicalInfo -> Unit

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
			val nextBlock = blocks.getOrNull(index + 1)
			if (nextBlock != null) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(if (isLandscape) 24.dp else 30.dp),
					contentAlignment = Alignment.Center
				) {
					if (showTechnicalInfo && shouldOverlayTechnicalInfoBetween(block, nextBlock)) {
						NowPlayingTechnicalInfoRow()
					}
				}
			}
		}
	}
}

@Composable
private fun NowPlayingTimelineBlock(
	upNextLayout: paige.navic.domain.models.NowPlayingUpNextLayout
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		NowPlayingProgressBar(modifier = Modifier.fillMaxWidth())
		NowPlayingDurationsRow()
		NowPlayingUpNextRow(layout = upNextLayout)
	}
}
