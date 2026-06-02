package paige.navic.ui.components.layouts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import paige.navic.domain.models.settings.NavbarConfig
import paige.navic.domain.models.settings.NavbarTab
import paige.navic.domain.models.settings.NavigationBarLabelVisibility
import paige.navic.domain.models.settings.NavigationBarStyle
import paige.navic.icons.Icons
import paige.navic.icons.filled.Album
import paige.navic.icons.filled.Artist
import paige.navic.icons.filled.Genre
import paige.navic.icons.filled.LibraryMusic
import paige.navic.icons.filled.Radio
import paige.navic.icons.outlined.Album
import paige.navic.icons.outlined.Artist
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
		icon = Icons.Filled.LibraryMusic,
		iconUnselected = Icons.Outlined.LibraryMusic,
		label = Res.string.title_audiobooks
	),
	BOOKS(
		id = NavbarTab.Id.BOOKS,
		destination = Screen.BinderyBooks,
		icon = Icons.Outlined.Note,
		label = Res.string.title_audiobook_books
	),
	COLLECTIONS(
		id = NavbarTab.Id.COLLECTIONS,
		destination = Screen.BinderyCollections,
		icon = Icons.Outlined.PlaylistPlay,
		label = Res.string.title_audiobook_collections
	),
	AUTHORS(
		id = NavbarTab.Id.AUTHORS,
		destination = Screen.BinderyAuthors,
		icon = Icons.Filled.Artist,
		iconUnselected = Icons.Outlined.Artist,
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

	AnimatedContent(
		preferenceManager.navigationBarStyle != NavigationBarStyle.Short
			&& platformContext.sizeClass.widthSizeClass <= WindowWidthSizeClass.Compact
			&& tabs.size > 1
	) {
		if (tabs.size < 2) return@AnimatedContent
		if (it) {
			NavigationBar(
				modifier = modifier,
				containerColor = containerColor,
				windowInsets = windowInsets
			) {
				tabs.forEach { tab ->
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
							backStack.apply {
								clear()
								add(item.destination)
							}
						},
						icon = {
							if (selected) {
								val painter = animatedTabIconPainter(item.destination)
								if (painter != null) {
									Icon(painter = painter, null)
								} else {
									Icon(item.icon, null)
								}
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
				tabs.forEach { tab ->
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
							backStack.apply {
								clear()
								add(item.destination)
							}
						},
						icon = {
							if (selected) {
								val painter = animatedTabIconPainter(item.destination)
								if (painter != null) {
									Icon(painter = painter, null)
								} else {
									Icon(item.icon, null)
								}
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

private fun navItemFromId(id: NavbarTab.Id): NavItem? =
	NavItem.entries.firstOrNull { it.id == id }

private fun NavItem.isSelected(screen: Screen?): Boolean =
	when (this) {
		NavItem.LIBRARY -> screen is Screen.Library
		NavItem.ALBUMS -> screen is Screen.AlbumList
		NavItem.PLAYLISTS -> screen is Screen.PlaylistList
		NavItem.ARTISTS -> screen is Screen.ArtistList
		NavItem.AUDIOBOOKS -> screen == Screen.Audiobooks
		NavItem.BOOKS -> screen == Screen.BinderyBooks
		NavItem.COLLECTIONS -> screen == Screen.BinderyCollections
		NavItem.AUTHORS -> screen == Screen.BinderyAuthors
		NavItem.ACTIVITY -> screen == Screen.Activity
		NavItem.SEARCH -> screen is Screen.Search
		NavItem.GENRES -> screen is Screen.GenreList || screen is Screen.GenreDetail
		NavItem.SONGS -> screen is Screen.SongList
		NavItem.RADIOS -> screen is Screen.RadioList
	}
