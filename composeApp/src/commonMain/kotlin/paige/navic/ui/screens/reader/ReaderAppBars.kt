package paige.navic.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import paige.navic.icons.Icons
import paige.navic.icons.filled.Settings
import paige.navic.icons.outlined.ArrowBack
import paige.navic.icons.outlined.Book
import paige.navic.icons.outlined.Bookmark
import paige.navic.icons.outlined.BookmarkBorder
import paige.navic.icons.outlined.List
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderDirectionRtl
import paige.navic.reader.ReaderNavBarTypeBottom
import paige.navic.reader.ReaderNavBarTypeVerticalLeft
import paige.navic.reader.ReaderNavBarTypeVerticalRight
import paige.navic.reader.normalizedReaderDirection
import paige.navic.reader.normalizedReaderNavBarType
import paige.navic.ui.navigation.Screen

private val readerBarsSlideAnimationSpec = tween<IntOffset>(200)
private val readerBarsFadeAnimationSpec = tween<Float>(150)

@Composable
internal fun KomikkuReaderAppBars(
	visible: Boolean,
	reader: Screen.Reader,
	controllerState: ReaderControllerState,
	onPreviousPage: () -> Unit,
	onNextPage: () -> Unit,
	onGoToChapterPage: (Int) -> Unit,
	onContents: () -> Unit,
	onReadingMode: () -> Unit,
	onNavigateBack: () -> Unit,
	onSettings: () -> Unit,
	onToggleCurrentBookmark: () -> Unit,
	modifier: Modifier = Modifier
) {
	// Ported from Komikku ReaderAppBars: all controls are overlays, never content padding.
	val chapterProgress = controllerState.chapterProgress
	val chapterTitle = when {
		controllerState.shellCoverVisible -> "Cover"
		!chapterProgress.title.isNullOrBlank() -> chapterProgress.title
		!controllerState.chrome.currentSectionTitle.isNullOrBlank() -> controllerState.chrome.currentSectionTitle
		else -> controllerState.chrome.progressLabel
	}
	val navBarType = normalizedReaderNavBarType(controllerState.chrome.settings.navBarType)
	val isRtl = normalizedReaderDirection(controllerState.chrome.settings.direction) == ReaderDirectionRtl
	Column(modifier = modifier.fillMaxHeight()) {
		AnimatedVisibility(
			visible = visible,
			enter = slideInVertically(initialOffsetY = { -it }, animationSpec = readerBarsSlideAnimationSpec) +
				fadeIn(animationSpec = readerBarsFadeAnimationSpec),
			exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = readerBarsSlideAnimationSpec) +
				fadeOut(animationSpec = readerBarsFadeAnimationSpec)
		) {
			KomikkuReaderTopBar(
				title = reader.title,
				chapterTitle = chapterTitle,
				bookmarked = controllerState.currentLocationBookmarked,
				canBookmark = controllerState.canBookmarkCurrentLocation,
				onNavigateBack = onNavigateBack,
				onToggleBookmarked = onToggleCurrentBookmark,
				modifier = Modifier.fillMaxWidth()
			)
		}

		when (navBarType) {
			ReaderNavBarTypeVerticalLeft -> {
				AnimatedVisibility(
					visible = visible,
					enter = slideInHorizontally(
						initialOffsetX = { -it },
						animationSpec = readerBarsSlideAnimationSpec
					) + fadeIn(animationSpec = readerBarsFadeAnimationSpec),
					exit = slideOutHorizontally(
						targetOffsetX = { -it },
						animationSpec = readerBarsSlideAnimationSpec
					) + fadeOut(animationSpec = readerBarsFadeAnimationSpec),
					modifier = Modifier
						.weight(1f)
						.align(Alignment.Start)
				) {
					KomikkuChapterNavigator(
						isRtl = isRtl,
						isVerticalSlider = true,
						onNextChapter = onNextPage,
						enabledNext = true,
						onPreviousChapter = onPreviousPage,
						enabledPrevious = !controllerState.shellCoverVisible,
						currentPage = chapterProgress.displayPage,
						currentPageText = chapterProgress.displayPage.toString(),
						totalPages = chapterProgress.pageCount,
						onPageIndexChange = { pageIndex ->
							onGoToChapterPage(pageIndex)
						}
					)
				}
			}

			ReaderNavBarTypeVerticalRight -> {
				AnimatedVisibility(
					visible = visible,
					enter = slideInHorizontally(
						initialOffsetX = { it },
						animationSpec = readerBarsSlideAnimationSpec
					) + fadeIn(animationSpec = readerBarsFadeAnimationSpec),
					exit = slideOutHorizontally(
						targetOffsetX = { it },
						animationSpec = readerBarsSlideAnimationSpec
					) + fadeOut(animationSpec = readerBarsFadeAnimationSpec),
					modifier = Modifier
						.weight(1f)
						.align(Alignment.End)
				) {
					KomikkuChapterNavigator(
						isRtl = isRtl,
						isVerticalSlider = true,
						onNextChapter = onNextPage,
						enabledNext = true,
						onPreviousChapter = onPreviousPage,
						enabledPrevious = !controllerState.shellCoverVisible,
						currentPage = chapterProgress.displayPage,
						currentPageText = chapterProgress.displayPage.toString(),
						totalPages = chapterProgress.pageCount,
						onPageIndexChange = { pageIndex ->
							onGoToChapterPage(pageIndex)
						}
					)
				}
			}

			ReaderNavBarTypeBottom -> {
				Spacer(modifier = Modifier.weight(1f))
			}
		}

		AnimatedVisibility(
			visible = visible,
			enter = slideInVertically(initialOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
				fadeIn(animationSpec = readerBarsFadeAnimationSpec),
			exit = slideOutVertically(targetOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
				fadeOut(animationSpec = readerBarsFadeAnimationSpec)
		) {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				if (navBarType == ReaderNavBarTypeBottom) {
					KomikkuChapterNavigator(
						isRtl = isRtl,
						isVerticalSlider = false,
						onNextChapter = onNextPage,
						enabledNext = true,
						onPreviousChapter = onPreviousPage,
						enabledPrevious = !controllerState.shellCoverVisible,
						currentPage = chapterProgress.displayPage,
						currentPageText = chapterProgress.displayPage.toString(),
						totalPages = chapterProgress.pageCount,
						onPageIndexChange = { pageIndex ->
							onGoToChapterPage(pageIndex)
						}
					)
				}
				KomikkuReaderBottomBar(
					onContents = onContents,
					onReadingMode = onReadingMode,
					onSettings = onSettings,
					modifier = Modifier.fillMaxWidth()
				)
			}
		}
	}
}

@Composable
private fun KomikkuReaderTopBar(
	title: String,
	chapterTitle: String,
	bookmarked: Boolean,
	canBookmark: Boolean,
	onNavigateBack: () -> Unit,
	onToggleBookmarked: () -> Unit,
	modifier: Modifier = Modifier
) {
	val backgroundColor = MaterialTheme.colorScheme
		.surfaceColorAtElevation(3.dp)
		.copy(alpha = 0.92f)

	Surface(
		color = backgroundColor,
		contentColor = MaterialTheme.colorScheme.onSurface,
		modifier = modifier
			.pointerInput(Unit) {}
	) {
		Row(
			modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(14.dp)
		) {
			IconButton(onClick = onNavigateBack) {
				Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
			}
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp)
			) {
				Text(
					text = title,
					style = MaterialTheme.typography.headlineSmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Text(
					text = chapterTitle,
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
			IconButton(
				enabled = canBookmark,
				onClick = onToggleBookmarked
			) {
				Icon(
					if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
					contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark"
				)
			}
		}
	}
}

@Composable
private fun KomikkuReaderBottomBar(
	onContents: () -> Unit,
	onReadingMode: () -> Unit,
	onSettings: () -> Unit,
	modifier: Modifier = Modifier
) {
	val backgroundColor = MaterialTheme.colorScheme
		.surfaceColorAtElevation(3.dp)
		.copy(alpha = 0.92f)
	val iconColor = MaterialTheme.colorScheme.primary

	Surface(
		color = backgroundColor,
		contentColor = iconColor,
		modifier = modifier
			.pointerInput(Unit) {}
	) {
		// Ported from Komikku ReaderBottomBar: centered, evenly distributed actions.
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 36.dp, vertical = 12.dp),
			horizontalArrangement = Arrangement.SpaceEvenly,
			verticalAlignment = Alignment.CenterVertically
		) {
			IconButton(onClick = onContents) {
				Icon(Icons.Outlined.List, contentDescription = "Contents", tint = iconColor)
			}
			IconButton(onClick = onReadingMode) {
				Icon(Icons.Outlined.Book, contentDescription = "Reading mode", tint = iconColor)
			}
			IconButton(onClick = onSettings) {
				Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = iconColor)
			}
		}
	}
}
