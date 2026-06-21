package paige.navic.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_chapters
import navic.composeapp.generated.resources.title_settings
import org.jetbrains.compose.resources.stringResource
import paige.navic.icons.Icons
import paige.navic.icons.outlined.ArrowBack
import paige.navic.icons.outlined.Bookmark
import paige.navic.icons.outlined.BookmarkBorder
import paige.navic.icons.outlined.FormatListNumbered
import paige.navic.icons.outlined.Search
import paige.navic.icons.outlined.Settings
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
	onPreviousChapter: () -> Unit,
	onNextChapter: () -> Unit,
	onGoToChapterPage: (Int) -> Unit,
	onContents: () -> Unit,
	onSearch: () -> Unit,
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
	val enabledButtons = KomikkuReaderBottomButton.NAVIC_SUPPORTED_DEFAULTS
	val backgroundColor = MaterialTheme.colorScheme
		.surfaceColorAtElevation(3.dp)
		.copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)
	val showChapterNavigator = visible && !controllerState.shellCoverVisible
	val showBottomBar = visible && !controllerState.shellCoverVisible
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
				modifier = Modifier
					.fillMaxWidth()
					.background(backgroundColor)
					.pointerInput(Unit) {}
			)
		}

		when (navBarType) {
			ReaderNavBarTypeVerticalLeft -> {
				AnimatedVisibility(
					visible = showChapterNavigator,
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
						onNextChapter = onNextChapter,
						enabledNext = controllerState.canNavigateToNextChapter,
						onPreviousChapter = onPreviousChapter,
						enabledPrevious = controllerState.canNavigateToPreviousChapter,
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
					visible = showChapterNavigator,
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
						onNextChapter = onNextChapter,
						enabledNext = controllerState.canNavigateToNextChapter,
						onPreviousChapter = onPreviousChapter,
						enabledPrevious = controllerState.canNavigateToPreviousChapter,
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
			visible = showBottomBar,
			enter = slideInVertically(initialOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
				fadeIn(animationSpec = readerBarsFadeAnimationSpec),
			exit = slideOutVertically(targetOffsetY = { it }, animationSpec = readerBarsSlideAnimationSpec) +
				fadeOut(animationSpec = readerBarsFadeAnimationSpec)
		) {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				if (navBarType == ReaderNavBarTypeBottom && !controllerState.shellCoverVisible) {
					KomikkuChapterNavigator(
						isRtl = isRtl,
						isVerticalSlider = false,
						onNextChapter = onNextChapter,
						enabledNext = controllerState.canNavigateToNextChapter,
						onPreviousChapter = onPreviousChapter,
						enabledPrevious = controllerState.canNavigateToPreviousChapter,
						currentPage = chapterProgress.displayPage,
						currentPageText = chapterProgress.displayPage.toString(),
						totalPages = chapterProgress.pageCount,
						onPageIndexChange = { pageIndex ->
							onGoToChapterPage(pageIndex)
						}
					)
				}
				KomikkuReaderBottomBar(
					enabledButtons = enabledButtons,
					onContents = onContents,
					onSearch = onSearch,
					onSettings = onSettings,
					modifier = Modifier
						.fillMaxWidth()
						.background(backgroundColor)
						.padding(horizontal = 36.dp, vertical = 12.dp)
						.windowInsetsPadding(WindowInsets.navigationBars)
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
	KomikkuReaderAppBar(
		modifier = modifier,
		backgroundColor = Color.Transparent,
		title = title,
		subtitle = chapterTitle,
		navigateUp = onNavigateBack,
		actions = {
			KomikkuReaderAppBarActions(
				actions = listOf(
					KomikkuReaderAppBarAction.Action(
						title = if (bookmarked) "Remove bookmark" else "Bookmark",
						icon = if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
						onClick = onToggleBookmarked,
						enabled = canBookmark
					)
				)
			)
		}
	)
}

@Composable
private fun KomikkuReaderAppBar(
	title: String?,
	subtitle: String?,
	navigateUp: (() -> Unit)?,
	modifier: Modifier = Modifier,
	backgroundColor: Color? = null,
	actions: @Composable RowScope.() -> Unit = {}
) {
	TopAppBar(
		modifier = modifier,
		navigationIcon = {
			navigateUp?.let {
				IconButton(onClick = it) {
					Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
				}
			}
		},
		title = {
			KomikkuReaderAppBarTitle(title = title, subtitle = subtitle)
		},
		actions = actions,
		colors = TopAppBarDefaults.topAppBarColors(
			containerColor = backgroundColor ?: MaterialTheme.colorScheme.surfaceColorAtElevation(0.dp)
		)
	)
}

@Composable
private fun KomikkuReaderAppBarTitle(
	title: String?,
	subtitle: String?,
	modifier: Modifier = Modifier
) {
	Column(modifier = modifier) {
		title?.let {
			Text(
				text = it,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
		subtitle?.let {
			Text(
				text = it,
				style = MaterialTheme.typography.bodyMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.basicMarquee(repeatDelayMillis = 2_000)
			)
		}
	}
}

@Composable
private fun KomikkuReaderAppBarActions(
	actions: List<KomikkuReaderAppBarAction>
) {
	actions.filterIsInstance<KomikkuReaderAppBarAction.Action>().forEach { action ->
		IconButton(
			onClick = action.onClick,
			enabled = action.enabled
		) {
			Icon(
				imageVector = action.icon,
				tint = action.iconTint ?: LocalContentColor.current,
				contentDescription = action.title
			)
		}
	}
}

private sealed interface KomikkuReaderAppBarAction {
	data class Action(
		val title: String,
		val icon: ImageVector,
		val iconTint: Color? = null,
		val onClick: () -> Unit,
		val enabled: Boolean = true
	) : KomikkuReaderAppBarAction
}

@Composable
private fun KomikkuReaderBottomBar(
	enabledButtons: Set<String>,
	onContents: () -> Unit,
	onSearch: () -> Unit,
	onSettings: () -> Unit,
	modifier: Modifier = Modifier
) {
	val iconColor = MaterialTheme.colorScheme.primary

	// Ported from Komikku ReaderBottomBar: centered, evenly distributed actions.
	Row(
		modifier = modifier
			.fillMaxWidth()
			.pointerInput(Unit) {},
		horizontalArrangement = Arrangement.SpaceEvenly,
		verticalAlignment = Alignment.CenterVertically
	) {
		if (KomikkuReaderBottomButton.ViewChapters.isIn(enabledButtons)) {
			IconButton(onClick = onContents) {
				Icon(
					imageVector = Icons.Outlined.FormatListNumbered,
					contentDescription = stringResource(Res.string.title_chapters),
					tint = iconColor
				)
			}
		}

		if (KomikkuReaderBottomButton.Search.isIn(enabledButtons)) {
			IconButton(onClick = onSearch) {
				Icon(
					imageVector = Icons.Outlined.Search,
					contentDescription = "Search",
					tint = iconColor
				)
			}
		}

		IconButton(onClick = onSettings) {
			Icon(
				imageVector = Icons.Outlined.Settings,
				contentDescription = stringResource(Res.string.title_settings),
				tint = iconColor
			)
		}
	}
}

private enum class KomikkuReaderBottomButton(val value: String) {
	ViewChapters("vc"),
	WebView("wb"),
	Browser("br"),
	Share("sh"),
	ReadingMode("rm"),
	Search("se"),
	Rotation("rot"),
	CropBordersPager("cbp"),
	CropBordersContinuesVertical("cbc"),
	CropBordersWebtoon("cbw"),
	PageLayout("pl");

	fun isIn(buttons: Collection<String>) = value in buttons

	companion object {
		val BUTTONS_DEFAULTS = setOf(
			ViewChapters,
			WebView,
			CropBordersPager,
			CropBordersContinuesVertical,
			PageLayout
		).map { it.value }.toSet()

		val NAVIC_SUPPORTED_DEFAULTS = setOf(
			ViewChapters,
			Search
		).map { it.value }.toSet()
	}
}
