package paige.navic.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import paige.navic.icons.Icons
import paige.navic.icons.filled.SkipNext
import paige.navic.icons.filled.SkipPrevious

@Composable
internal fun KomikkuChapterNavigator(
	isRtl: Boolean,
	isVerticalSlider: Boolean,
	onNextChapter: () -> Unit,
	enabledNext: Boolean,
	onPreviousChapter: () -> Unit,
	enabledPrevious: Boolean,
	currentPage: Int,
	currentPageText: String,
	totalPages: Int,
	onPageIndexChange: (Int) -> Unit,
	modifier: Modifier = Modifier
) {
	if (isVerticalSlider) {
		KomikkuChapterNavigatorVertical(
			onNextChapter = onNextChapter,
			enabledNext = enabledNext,
			onPreviousChapter = onPreviousChapter,
			enabledPrevious = enabledPrevious,
			currentPage = currentPage,
			currentPageText = currentPageText,
			totalPages = totalPages,
			onPageIndexChange = onPageIndexChange,
			modifier = modifier
		)
		return
	}

	val backgroundColor = MaterialTheme.colorScheme
		.surfaceColorAtElevation(3.dp)
		.copy(alpha = 0.92f)
	val buttonColor = IconButtonDefaults.filledIconButtonColors(
		containerColor = backgroundColor,
		disabledContainerColor = backgroundColor,
		contentColor = MaterialTheme.colorScheme.primary
	)
	val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

	CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
		Row(
			modifier = modifier
				.fillMaxWidth()
				.padding(horizontal = 8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			FilledIconButton(
				enabled = if (isRtl) enabledNext else enabledPrevious,
				onClick = if (isRtl) onNextChapter else onPreviousChapter,
				colors = buttonColor
			) {
				Icon(
					Icons.Filled.SkipPrevious,
					contentDescription = if (isRtl) "Next" else "Previous"
				)
			}

			if (totalPages > 1) {
				CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
					Row(
						modifier = Modifier
							.weight(1f)
							.clip(RoundedCornerShape(24.dp))
							.background(backgroundColor)
							.padding(horizontal = 16.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Box(contentAlignment = Alignment.CenterEnd) {
							Text(text = currentPageText)
							Text(text = totalPages.toString(), color = Color.Transparent)
						}
						val haptic = LocalHapticFeedback.current
						val interactionSource = remember { MutableInteractionSource() }
						val sliderDragged by interactionSource.collectIsDraggedAsState()
						LaunchedEffect(currentPage) {
							if (sliderDragged) {
								haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
							}
						}
						KomikkuChapterProgressSlider(
							modifier = Modifier
								.weight(1f)
								.padding(horizontal = 8.dp),
							value = currentPage,
							valueRange = 1..totalPages,
							onValueChange = { page ->
								if (page != currentPage) {
									onPageIndexChange(page - 1)
								}
							},
							interactionSource = interactionSource
						)
						Text(text = totalPages.toString())
					}
				}
			} else {
				Spacer(Modifier.weight(1f))
			}

			FilledIconButton(
				enabled = if (isRtl) enabledPrevious else enabledNext,
				onClick = if (isRtl) onPreviousChapter else onNextChapter,
				colors = buttonColor
			) {
				Icon(
					Icons.Filled.SkipNext,
					contentDescription = if (isRtl) "Previous" else "Next"
				)
			}
		}
	}
}

@Composable
private fun KomikkuChapterProgressSlider(
	value: Int,
	valueRange: IntProgression,
	onValueChange: (Int) -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
	Slider(
		value = value.toFloat(),
		onValueChange = { changedValue -> onValueChange(changedValue.roundToInt()) },
		modifier = modifier,
		enabled = enabled,
		valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
		steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
		interactionSource = interactionSource
	)
}

@Composable
private fun KomikkuChapterNavigatorVertical(
	onNextChapter: () -> Unit,
	enabledNext: Boolean,
	onPreviousChapter: () -> Unit,
	enabledPrevious: Boolean,
	currentPage: Int,
	currentPageText: String,
	totalPages: Int,
	onPageIndexChange: (Int) -> Unit,
	modifier: Modifier = Modifier
) {
	val backgroundColor = MaterialTheme.colorScheme
		.surfaceColorAtElevation(3.dp)
		.copy(alpha = 0.92f)
	val buttonColor = IconButtonDefaults.filledIconButtonColors(
		containerColor = backgroundColor,
		disabledContainerColor = backgroundColor,
		contentColor = MaterialTheme.colorScheme.primary
	)

	Column(
		modifier = modifier
			.fillMaxHeight()
			.padding(vertical = 8.dp, horizontal = 8.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		FilledIconButton(
			enabled = enabledPrevious,
			onClick = onPreviousChapter,
			colors = buttonColor
		) {
			Icon(
				Icons.Filled.SkipPrevious,
				contentDescription = "Previous",
				modifier = Modifier.rotate(90f)
			)
		}

		if (totalPages > 1) {
			Column(
				modifier = Modifier
					.weight(1f)
					.clip(RoundedCornerShape(24.dp))
					.background(backgroundColor)
					.padding(vertical = 16.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Text(text = currentPageText)
				val haptic = LocalHapticFeedback.current
				val interactionSource = remember { MutableInteractionSource() }
				val sliderDragged by interactionSource.collectIsDraggedAsState()
				LaunchedEffect(currentPage) {
					if (sliderDragged) {
						haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
					}
				}
				KomikkuChapterProgressSlider(
					modifier = Modifier
						.padding(vertical = 8.dp)
						.graphicsLayer {
							rotationZ = 90f
							transformOrigin = TransformOrigin(0f, 0f)
						}
						.layout { measurable, constraints ->
							val placeable = measurable.measure(
								Constraints(
									minWidth = constraints.minHeight,
									maxWidth = constraints.maxHeight,
									minHeight = constraints.minWidth,
									maxHeight = constraints.maxWidth
								)
							)
							layout(placeable.height, placeable.width) {
								placeable.place(0, -placeable.height)
							}
						}
						.weight(1f),
					value = currentPage,
					valueRange = 1..totalPages,
					onValueChange = { page ->
						if (page != currentPage) {
							onPageIndexChange(page - 1)
						}
					},
					interactionSource = interactionSource
				)
				Text(text = totalPages.toString())
			}
		} else {
			Spacer(Modifier.weight(1f))
		}

		FilledIconButton(
			enabled = enabledNext,
			onClick = onNextChapter,
			colors = buttonColor
		) {
			Icon(
				Icons.Filled.SkipNext,
				contentDescription = "Next",
				modifier = Modifier.rotate(90f)
			)
		}
	}
}
