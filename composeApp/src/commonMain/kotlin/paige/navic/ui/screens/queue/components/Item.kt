package paige.navic.ui.screens.queue.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_remove_from_queue
import navic.composeapp.generated.resources.action_reorder
import navic.composeapp.generated.resources.info_not_available_offline
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.SongSwipeDirection
import paige.navic.domain.models.queueSwipeActionForDirection
import paige.navic.domain.models.settings.QueueSwipeAction
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Delete
import paige.navic.icons.outlined.DragHandle
import paige.navic.icons.outlined.Offline
import paige.navic.icons.outlined.QueuePlayNext
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.MarqueeText
import paige.navic.ui.components.common.Waveform
import paige.navic.util.ui.DraggableListState
import paige.navic.util.ui.dragHandle
import paige.navic.util.ui.segmentedShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QueueScreenItem(
	index: Int,
	count: Int,
	song: DomainSong,
	isPlaying: Boolean,
	isSelected: Boolean,
	isDragging: Boolean,
	draggableState: DraggableListState,
	onClick: () -> Unit,
	onRemove: () -> Unit,
	onPlayNext: () -> Unit,
	queueSwipeActionsEnabled: Boolean = true,
	queueSwipeStartToEndAction: QueueSwipeAction = QueueSwipeAction.RemoveFromQueue,
	queueSwipeEndToStartAction: QueueSwipeAction = QueueSwipeAction.RemoveFromQueue,
	isOffline: Boolean = false,
	isDownloaded: Boolean = false
) {
	val canPlay = !isOffline || isDownloaded

	val elevation by animateDpAsState(
		targetValue = if (isDragging) 8.dp else 0.dp,
		animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
	)

	val dismissState = rememberSwipeToDismissBoxState()
	val scope = rememberCoroutineScope()

	val color = if (isSelected)
		MaterialTheme.colorScheme.surfaceContainerHighest
	else MaterialTheme.colorScheme.surfaceContainerHigh

	val contentColor = if (isSelected)
		MaterialTheme.colorScheme.primary
	else MaterialTheme.colorScheme.onSurface

	val supportingContentColor = if (isSelected)
		MaterialTheme.colorScheme.primary.copy(alpha = .7f)
	else MaterialTheme.colorScheme.onSurfaceVariant

	val itemShape = segmentedShapes(
		index = index,
		count = count,
		dismissDirection = dismissState.dismissDirection
	)
	val startToEndSwipeAction = queueSwipeActionForDirection(
		enabled = queueSwipeActionsEnabled,
		startToEndAction = queueSwipeStartToEndAction,
		endToStartAction = queueSwipeEndToStartAction,
		direction = SongSwipeDirection.StartToEnd
	)
	val endToStartSwipeAction = queueSwipeActionForDirection(
		enabled = queueSwipeActionsEnabled,
		startToEndAction = queueSwipeStartToEndAction,
		endToStartAction = queueSwipeEndToStartAction,
		direction = SongSwipeDirection.EndToStart
	)
	val visibleSwipeAction = when (dismissState.dismissDirection) {
		SwipeToDismissBoxValue.StartToEnd -> startToEndSwipeAction
		SwipeToDismissBoxValue.EndToStart -> endToStartSwipeAction
		else -> QueueSwipeAction.Disabled
	}
	val swipeBackgroundColor = when (visibleSwipeAction) {
		QueueSwipeAction.RemoveFromQueue -> MaterialTheme.colorScheme.errorContainer
		QueueSwipeAction.PlayNext -> MaterialTheme.colorScheme.primaryContainer
		QueueSwipeAction.Disabled -> MaterialTheme.colorScheme.surfaceVariant
	}

	SwipeToDismissBox(
		state = dismissState,
		onDismiss = {
			when (
				if (it == SwipeToDismissBoxValue.StartToEnd) {
					startToEndSwipeAction
				} else {
					endToStartSwipeAction
				}
			) {
				QueueSwipeAction.RemoveFromQueue -> onRemove()
				QueueSwipeAction.PlayNext -> onPlayNext()
				QueueSwipeAction.Disabled -> Unit
			}
			scope.launch {
				dismissState.reset()
			}
		},
		backgroundContent = {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clip(itemShape.shape)
					.background(swipeBackgroundColor)
					.padding(horizontal = 20.dp)
			) {
				when (dismissState.dismissDirection) {
					SwipeToDismissBoxValue.StartToEnd -> QueueSwipeActionIcon(
						action = startToEndSwipeAction,
						alignment = Alignment.CenterStart
					)
					SwipeToDismissBoxValue.EndToStart -> QueueSwipeActionIcon(
						action = endToStartSwipeAction,
						alignment = Alignment.CenterEnd
					)
					else -> Unit
				}
			}
		},
		content = {
			Surface(
				shadowElevation = elevation,
				shape = itemShape.shape
			) {
				SegmentedListItem(
					onClick = onClick,
					enabled = canPlay,
					colors = ListItemDefaults.colors(
						containerColor = color,
						selectedContainerColor = color,
						disabledContainerColor = color,
						draggedContainerColor = color,
						contentColor = contentColor,
						supportingContentColor = supportingContentColor
					),
					shapes = itemShape,
					verticalAlignment = Alignment.CenterVertically,
					content = { MarqueeText(song.title) },
					supportingContent = { MarqueeText(song.artistName) },
					leadingContent = {
						CoverArt(
							modifier = Modifier.size(48.dp),
							coverArtId = song.coverArtId,
							shape = ContinuousRoundedRectangle(10.dp)
						)
					},
					trailingContent = {
						Row(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							if (!canPlay) {
								Icon(
									Icons.Outlined.Offline,
									stringResource(Res.string.info_not_available_offline),
									modifier = Modifier.size(20.dp)
								)
							}
							if (isSelected) {
								Waveform(isPlaying = isPlaying)
							}
							IconButton(
								modifier = Modifier.dragHandle(
									state = draggableState,
									index = index
								),
								onClick = {}
							) {
								Icon(
									Icons.Outlined.DragHandle,
									contentDescription = stringResource(Res.string.action_reorder)
								)
							}
						}
					},
					contentPadding = PaddingValues(10.dp)
				)
			}
		}
	)
}

@Composable
private fun BoxScope.QueueSwipeActionIcon(
	action: QueueSwipeAction,
	alignment: Alignment
) {
	when (action) {
		QueueSwipeAction.RemoveFromQueue -> Icon(
			imageVector = Icons.Outlined.Delete,
			contentDescription = stringResource(action.displayName),
			tint = MaterialTheme.colorScheme.onErrorContainer,
			modifier = Modifier.align(alignment)
		)
		QueueSwipeAction.PlayNext -> Icon(
			imageVector = Icons.Outlined.QueuePlayNext,
			contentDescription = stringResource(action.displayName),
			tint = MaterialTheme.colorScheme.onPrimaryContainer,
			modifier = Modifier.align(alignment)
		)
		QueueSwipeAction.Disabled -> Unit
	}
}
