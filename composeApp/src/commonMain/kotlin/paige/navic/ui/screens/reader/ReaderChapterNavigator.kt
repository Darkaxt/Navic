package paige.navic.ui.screens.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_next_chapter
import navic.composeapp.generated.resources.action_previous_chapter
import org.jetbrains.compose.resources.stringResource
import paige.navic.icons.Icons
import paige.navic.icons.outlined.SkipNext
import paige.navic.icons.outlined.SkipPrevious
import kotlin.math.roundToInt

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

	val isTabletUi = komikkuReaderIsTabletUi()
	val horizontalPadding = if (isTabletUi) 24.dp else 8.dp
	val backgroundColor = MaterialTheme.colorScheme
		.surfaceColorAtElevation(3.dp)
		.copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)
	val buttonColor = IconButtonDefaults.filledIconButtonColors(
		containerColor = backgroundColor,
		disabledContainerColor = backgroundColor,
		contentColor = MaterialTheme.colorScheme.primary
	)
	val textColor = MaterialTheme.colorScheme.onSurface
	val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

	CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
		Row(
			modifier = modifier
				.fillMaxWidth()
				.padding(horizontal = horizontalPadding),
			verticalAlignment = Alignment.CenterVertically
		) {
			FilledIconButton(
				enabled = if (isRtl) enabledNext else enabledPrevious,
				onClick = if (isRtl) onNextChapter else onPreviousChapter,
				colors = buttonColor
			) {
				Icon(
					Icons.Outlined.SkipPrevious,
					contentDescription = stringResource(
						if (isRtl) Res.string.action_next_chapter else Res.string.action_previous_chapter
					)
				)
			}

			if (readerShouldShowChapterProgressSlider(totalPages)) {
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
							Text(
								text = currentPageText,
								color = textColor
							)
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
								.padding(horizontal = 8.dp)
								.semantics(mergeDescendants = true) {
									contentDescription = "Chapter page slider"
								},
							value = currentPage,
							valueRange = 1..totalPages,
							onValueChange = { page ->
								if (page != currentPage) {
									onPageIndexChange(page - 1)
								}
							},
							interactionSource = interactionSource
						)
						Text(
							text = totalPages.toString(),
							color = textColor
						)
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
					Icons.Outlined.SkipNext,
					contentDescription = stringResource(
						if (isRtl) Res.string.action_previous_chapter else Res.string.action_next_chapter
					)
				)
			}
		}
	}
}

private fun readerShouldShowChapterProgressSlider(totalPages: Int): Boolean = totalPages >= 3

@Composable
private fun KomikkuChapterProgressSlider(
	value: Int,
	valueRange: IntProgression,
	onValueChange: (Int) -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
	KomikkuIntegerSlider(
		value = value,
		onValueChange = onValueChange,
		modifier = modifier,
		enabled = enabled,
		valueRange = valueRange,
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
	val isTabletUi = komikkuReaderIsTabletUi()
	val verticalPadding = if (isTabletUi) 24.dp else 8.dp
	val backgroundColor = MaterialTheme.colorScheme
		.surfaceColorAtElevation(3.dp)
		.copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)
	val buttonColor = IconButtonDefaults.filledIconButtonColors(
		containerColor = backgroundColor,
		disabledContainerColor = backgroundColor,
		contentColor = MaterialTheme.colorScheme.primary
	)
	val textColor = MaterialTheme.colorScheme.onSurface

	Column(
		modifier = modifier
			.fillMaxHeight()
			.padding(vertical = verticalPadding, horizontal = 8.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		FilledIconButton(
			enabled = enabledPrevious,
			onClick = onPreviousChapter,
			colors = buttonColor
		) {
			Icon(
				Icons.Outlined.SkipPrevious,
				contentDescription = stringResource(Res.string.action_previous_chapter),
				modifier = Modifier.rotate(90f)
			)
		}

		if (readerShouldShowChapterProgressSlider(totalPages)) {
			Column(
				modifier = Modifier
					.weight(1f)
					.clip(RoundedCornerShape(24.dp))
					.background(backgroundColor)
					.padding(vertical = 16.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Text(
					text = currentPageText,
					color = textColor
				)
				KomikkuVerticalChapterProgressRail(
					modifier = Modifier
						.weight(1f)
						.padding(vertical = 8.dp)
						.semantics(mergeDescendants = true) {
							contentDescription = "Chapter page slider"
						},
					currentPage = currentPage,
					totalPages = totalPages,
					onPageChange = { page -> onPageIndexChange(page - 1) }
				)
				Text(
					text = totalPages.toString(),
					color = textColor
				)
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
				Icons.Outlined.SkipNext,
				contentDescription = stringResource(Res.string.action_next_chapter),
				modifier = Modifier.rotate(90f)
			)
		}
	}
}

@Composable
private fun KomikkuVerticalChapterProgressRail(
	currentPage: Int,
	totalPages: Int,
	onPageChange: (Int) -> Unit,
	modifier: Modifier = Modifier
) {
	val haptic = LocalHapticFeedback.current
	val pageCount = totalPages.coerceAtLeast(1)
	val progress = komikkuChapterRailProgress(currentPage, pageCount)
	val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSystemInDarkTheme()) 0.18f else 0.14f)
	val activeColor = MaterialTheme.colorScheme.primary
	val tickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)

	fun emitPage(page: Int) {
		if (page != currentPage.coerceIn(1, pageCount)) {
			onPageChange(page)
			haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
		}
	}

	Box(
		modifier = modifier
			.width(52.dp)
			.pointerInput(pageCount, currentPage) {
				detectTapGestures { offset ->
					emitPage(
						komikkuChapterRailPageForOffset(
							offsetY = offset.y,
							heightPx = size.height.toFloat(),
							totalPages = pageCount
						)
					)
				}
			}
			.pointerInput(pageCount, currentPage) {
				detectDragGestures(
					onDragStart = { offset ->
						emitPage(
							komikkuChapterRailPageForOffset(
								offsetY = offset.y,
								heightPx = size.height.toFloat(),
								totalPages = pageCount
							)
						)
					},
					onDrag = { change, _ ->
						emitPage(
							komikkuChapterRailPageForOffset(
								offsetY = change.position.y,
								heightPx = size.height.toFloat(),
								totalPages = pageCount
							)
						)
						change.consume()
					}
				)
			},
		contentAlignment = Alignment.Center
	) {
		Canvas(modifier = Modifier.fillMaxSize()) {
			val railWidth = 22.dp.toPx().coerceAtMost(size.width)
			val railLeft = (size.width - railWidth) / 2f
			val railRadius = railWidth / 2f
			drawRoundRect(
				color = trackColor,
				topLeft = Offset(railLeft, 0f),
				size = Size(railWidth, size.height),
				cornerRadius = CornerRadius(railRadius, railRadius)
			)
			drawRoundRect(
				color = activeColor.copy(alpha = 0.7f),
				topLeft = Offset(railLeft, 0f),
				size = Size(railWidth, size.height * progress),
				cornerRadius = CornerRadius(railRadius, railRadius)
			)
			val tickCount = pageCount.coerceIn(2, 18)
			val tickRadius = 2.dp.toPx()
			repeat(tickCount) { index ->
				val tickProgress = if (tickCount <= 1) 0f else index.toFloat() / (tickCount - 1).toFloat()
				drawCircle(
					color = tickColor,
					radius = tickRadius,
					center = Offset(size.width / 2f, size.height * tickProgress)
				)
			}
			val thumbWidth = 34.dp.toPx().coerceAtMost(size.width)
			val thumbHeight = 64.dp.toPx().coerceAtMost(size.height)
			val thumbLeft = (size.width - thumbWidth) / 2f
			val thumbTop = (size.height * progress - thumbHeight / 2f)
				.coerceIn(0f, (size.height - thumbHeight).coerceAtLeast(0f))
			drawRoundRect(
				color = activeColor,
				topLeft = Offset(thumbLeft, thumbTop),
				size = Size(thumbWidth, thumbHeight),
				cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
			)
		}
	}
}

internal fun komikkuChapterRailPageForOffset(
	offsetY: Float,
	heightPx: Float,
	totalPages: Int
): Int {
	val pageCount = totalPages.coerceAtLeast(1)
	if (pageCount <= 1 || !offsetY.isFinite() || !heightPx.isFinite() || heightPx <= 0f) {
		return 1
	}
	val fraction = (offsetY / heightPx).coerceIn(0f, 1f)
	return (1 + (fraction * (pageCount - 1)).roundToInt()).coerceIn(1, pageCount)
}

private fun komikkuChapterRailProgress(
	currentPage: Int,
	totalPages: Int
): Float {
	val pageCount = totalPages.coerceAtLeast(1)
	if (pageCount <= 1) return 0f
	return ((currentPage.coerceIn(1, pageCount) - 1).toFloat() / (pageCount - 1).toFloat())
		.coerceIn(0f, 1f)
}
