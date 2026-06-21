package paige.navic.ui.components.layouts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_activity
import navic.composeapp.generated.resources.title_albums
import navic.composeapp.generated.resources.title_artists
import navic.composeapp.generated.resources.title_audiobook_authors
import navic.composeapp.generated.resources.title_audiobook_books
import navic.composeapp.generated.resources.title_audiobook_collections
import navic.composeapp.generated.resources.title_audiobooks
import navic.composeapp.generated.resources.title_genres
import navic.composeapp.generated.resources.title_library
import navic.composeapp.generated.resources.title_playlists
import navic.composeapp.generated.resources.title_radios
import navic.composeapp.generated.resources.title_search
import navic.composeapp.generated.resources.title_songs
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.settings.BottomBarProfile
import paige.navic.domain.models.settings.NavbarConfig
import paige.navic.domain.models.settings.NavbarTab
import paige.navic.domain.models.settings.NavigationBarLabelVisibility
import paige.navic.domain.models.settings.NavigationBarStyle
import paige.navic.icons.Icons
import paige.navic.icons.filled.Album
import paige.navic.icons.filled.Artist
import paige.navic.icons.filled.Author
import paige.navic.icons.filled.Genre
import paige.navic.icons.filled.LibraryMusic
import paige.navic.icons.filled.Radio
import paige.navic.icons.outlined.Album
import paige.navic.icons.outlined.Artist
import paige.navic.icons.outlined.Audiobooks
import paige.navic.icons.outlined.Author
import paige.navic.icons.outlined.Book
import paige.navic.icons.outlined.CollectionBooks
import paige.navic.icons.outlined.Genre
import paige.navic.icons.outlined.History
import paige.navic.icons.outlined.LibraryMusic
import paige.navic.icons.outlined.Note
import paige.navic.icons.outlined.PlaylistPlay
import paige.navic.icons.outlined.Radio
import paige.navic.icons.outlined.Search
import paige.navic.ui.components.common.animatedTabIconPainter
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.navigation.bottomBarProfileForScreen
import paige.navic.ui.navigation.bottomBarProfileForTabClick
import paige.navic.ui.navigation.navbarTabIdsForProfile
import paige.navic.ui.navigation.selectNavicRootTab
import paige.navic.ui.navigation.shouldUseSelectedTabIconFallbackMotion
import paige.navic.ui.screens.settings.viewmodels.NavtabsViewModel

private enum class NavItem(
	val id: NavbarTab.Id,
	val destination: Screen,
	val icon: ImageVector,
	val iconUnselected: ImageVector = icon,
	val label: StringResource
) {
	LIBRARY(
		id = NavbarTab.Id.LIBRARY,
		destination = Screen.Library(),
		icon = Icons.Filled.LibraryMusic,
		iconUnselected = Icons.Outlined.LibraryMusic,
		label = Res.string.title_library
	),
	ALBUMS(
		id = NavbarTab.Id.ALBUMS,
		destination = Screen.AlbumList(),
		icon = Icons.Filled.Album,
		iconUnselected = Icons.Outlined.Album,
		label = Res.string.title_albums
	),
	PLAYLISTS(
		id = NavbarTab.Id.PLAYLISTS,
		destination = Screen.PlaylistList(),
		icon = Icons.Outlined.PlaylistPlay,
		label = Res.string.title_playlists
	),
	ARTISTS(
		id = NavbarTab.Id.ARTISTS,
		destination = Screen.ArtistList(),
		icon = Icons.Filled.Artist,
		iconUnselected = Icons.Outlined.Artist,
		label = Res.string.title_artists
	),
	AUDIOBOOKS(
		id = NavbarTab.Id.AUDIOBOOKS,
		destination = Screen.Audiobooks,
		icon = Icons.Outlined.Audiobooks,
		iconUnselected = Icons.Outlined.Audiobooks,
		label = Res.string.title_audiobooks
	),
	BOOKS(
		id = NavbarTab.Id.BOOKS,
		destination = Screen.BinderyBooks,
		icon = Icons.Outlined.Book,
		label = Res.string.title_audiobook_books
	),
	COLLECTIONS(
		id = NavbarTab.Id.COLLECTIONS,
		destination = Screen.BinderyCollections,
		icon = Icons.Outlined.CollectionBooks,
		label = Res.string.title_audiobook_collections
	),
	AUTHORS(
		id = NavbarTab.Id.AUTHORS,
		destination = Screen.BinderyAuthors,
		icon = Icons.Filled.Author,
		iconUnselected = Icons.Outlined.Author,
		label = Res.string.title_audiobook_authors
	),
	ACTIVITY(
		id = NavbarTab.Id.ACTIVITY,
		destination = Screen.Activity,
		icon = Icons.Outlined.History,
		iconUnselected = Icons.Outlined.History,
		label = Res.string.title_activity
	),
	SEARCH(
		id = NavbarTab.Id.SEARCH,
		destination = Screen.Search(),
		icon = Icons.Outlined.Search,
		iconUnselected = Icons.Outlined.Search,
		label = Res.string.title_search
	),
	GENRES(
		id = NavbarTab.Id.GENRES,
		destination = Screen.GenreList(),
		icon = Icons.Filled.Genre,
		iconUnselected = Icons.Outlined.Genre,
		label = Res.string.title_genres
	),
	SONGS(
		id = NavbarTab.Id.SONGS,
		destination = Screen.SongList(),
		icon = Icons.Outlined.Note,
		iconUnselected = Icons.Outlined.Note,
		label = Res.string.title_songs
	),
	RADIOS(
		id = NavbarTab.Id.RADIOS,
		destination = Screen.RadioList(),
		icon = Icons.Filled.Radio,
		iconUnselected = Icons.Outlined.Radio,
		label = Res.string.title_radios
	)
}

@Immutable
private data class BottomBarRenderState(
	val regular: Boolean,
	val profile: BottomBarProfile,
	val tabIds: List<NavbarTab.Id>
)

@Composable
fun BottomBar(
	modifier: Modifier = Modifier,
	containerColor: Color = NavigationBarDefaults.containerColor,
	windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
	enabled: Boolean = true
) {
	val viewModel = koinViewModel<NavtabsViewModel>()
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val state by viewModel.state.collectAsState()
	val containerColor by animateColorAsState(containerColor)
	val preferenceManager = koinInject<PreferenceManager>()
	val config = (state as? UiState.Success)?.data ?: NavbarConfig.default
	val currentScreen = backStack.lastOrNull() as? Screen
	val activeProfile = bottomBarProfileForScreen(
		screen = currentScreen,
		rememberedProfile = preferenceManager.bottomBarProfile,
		binderyEnabled = preferenceManager.binderyEnabled
	)
	val tabs = navbarTabIdsForProfile(
		config = config,
		profile = activeProfile,
		binderyEnabled = preferenceManager.binderyEnabled
	).mapNotNull(::navItemFromId)
	val renderState = BottomBarRenderState(
		regular = preferenceManager.navigationBarStyle != NavigationBarStyle.Short
			&& platformContext.sizeClass.widthSizeClass <= WindowWidthSizeClass.Compact
			&& tabs.size > 1,
		profile = activeProfile,
		tabIds = tabs.map { it.id }
	)

	AnimatedContent(
		targetState = renderState,
		transitionSpec = {
			val direction = bottomBarProfileTransitionDirection(initialState, targetState)
			(fadeIn(animationSpec = tween(160)) +
				slideInHorizontally(animationSpec = tween(160)) { width -> width / 8 * direction })
				.togetherWith(
					fadeOut(animationSpec = tween(120)) +
						slideOutHorizontally(animationSpec = tween(120)) { width -> -width / 8 * direction }
				)
		}
	) { state ->
		val renderedTabs = state.tabIds.mapNotNull(::navItemFromId)
		if (renderedTabs.size < 2) return@AnimatedContent
		if (state.regular) {
			NavigationBar(
				modifier = modifier,
				containerColor = containerColor,
				windowInsets = windowInsets
			) {
				renderedTabs.forEach { tab ->
					val item = tab
					val selected = item.isSelected(currentScreen)

					NavigationBarItem(
						selected = selected,
						enabled = enabled,
						alwaysShowLabel = preferenceManager.navigationBarLabelVisibility
							== NavigationBarLabelVisibility.Always,
						onClick = {
							platformContext.clickSound()
							preferenceManager.bottomBarProfile =
								bottomBarProfileForTabClick(item.id, activeProfile)
							backStack.selectNavicRootTab(item.destination)
						},
						icon = {
							if (selected) {
								SelectedNavItemIcon(item)
							} else {
								Icon(item.iconUnselected, null)
							}
						},
						label = {
							Text(
								stringResource(item.label),
								maxLines = 1,
								autoSize = TextAutoSize.StepBased(
									minFontSize = 1.sp,
									maxFontSize = MaterialTheme.typography.labelMedium.fontSize
								)
							)
						}
					)
				}
			}
		} else {
			ShortNavigationBar(
				modifier = modifier,
				containerColor = containerColor
			) {
				renderedTabs.forEach { tab ->
					val item = tab
					val selected = item.isSelected(currentScreen)

					ShortNavigationBarItem(
						iconPosition = if (platformContext.sizeClass.widthSizeClass > WindowWidthSizeClass.Compact)
							NavigationItemIconPosition.Start
						else NavigationItemIconPosition.Top,
						selected = selected,
						enabled = enabled,
						onClick = {
							platformContext.clickSound()
							preferenceManager.bottomBarProfile =
								bottomBarProfileForTabClick(item.id, activeProfile)
							backStack.selectNavicRootTab(item.destination)
						},
						icon = {
							if (selected) {
								SelectedNavItemIcon(item)
							} else {
								Icon(item.iconUnselected, null)
							}
						},
						label = {
							Text(stringResource(item.label))
						}
					)
				}
			}
		}
	}
}

@Composable
private fun SelectedNavItemIcon(item: NavItem) {
	val painter = animatedTabIconPainter(item.destination)
	if (painter != null) {
		Icon(painter = painter, contentDescription = null)
		return
	}

	if (!shouldUseSelectedTabIconFallbackMotion(item.destination)) {
		Icon(item.icon, contentDescription = null)
		return
	}

	val rotation = remember(item.destination) { Animatable(-5f) }
	LaunchedEffect(item.destination) {
		rotation.snapTo(-5f)
		rotation.animateTo(4f, tween(durationMillis = 95, easing = FastOutSlowInEasing))
		rotation.animateTo(-2.5f, tween(durationMillis = 80, easing = FastOutSlowInEasing))
		rotation.animateTo(0f, tween(durationMillis = 80, easing = FastOutSlowInEasing))
	}
	Icon(
		imageVector = item.icon,
		contentDescription = null,
		modifier = Modifier.rotate(rotation.value)
	)
}

private fun bottomBarProfileTransitionDirection(
	initial: BottomBarRenderState,
	target: BottomBarRenderState
): Int =
	when {
		initial.profile == target.profile -> 0
		target.profile == BottomBarProfile.Audiobooks -> 1
		initial.profile == BottomBarProfile.Audiobooks -> -1
		else -> 0
	}

private fun navItemFromId(id: NavbarTab.Id): NavItem? =
	NavItem.entries.firstOrNull { it.id == id }

private fun NavItem.isSelected(screen: Screen?): Boolean =
	when (this) {
		NavItem.LIBRARY -> screen is Screen.Library
		NavItem.ALBUMS -> screen is Screen.AlbumList
		NavItem.PLAYLISTS -> screen is Screen.PlaylistList
		NavItem.ARTISTS -> screen is Screen.ArtistList
		NavItem.AUDIOBOOKS -> screen == Screen.Audiobooks
		NavItem.BOOKS -> screen == Screen.BinderyBooks || screen is Screen.BinderyBook
		NavItem.COLLECTIONS -> screen == Screen.BinderyCollections || screen is Screen.BinderyCollection
		NavItem.AUTHORS -> screen == Screen.BinderyAuthors || screen is Screen.BinderyAuthor
		NavItem.ACTIVITY -> screen == Screen.Activity
		NavItem.SEARCH -> screen is Screen.Search
		NavItem.GENRES -> screen is Screen.GenreList || screen is Screen.GenreDetail
		NavItem.SONGS -> screen is Screen.SongList
		NavItem.RADIOS -> screen is Screen.RadioList
	}
