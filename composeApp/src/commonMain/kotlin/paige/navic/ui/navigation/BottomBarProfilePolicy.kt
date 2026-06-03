package paige.navic.ui.navigation

import paige.navic.domain.models.settings.BottomBarProfile
import paige.navic.domain.models.settings.NavbarConfig
import paige.navic.domain.models.settings.NavbarTab

private val compactProfileTabs = listOf(
	NavbarTab.Id.LIBRARY,
	NavbarTab.Id.AUDIOBOOKS,
	NavbarTab.Id.ACTIVITY
)

private val musicProfileTabs = listOf(
	NavbarTab.Id.LIBRARY,
	NavbarTab.Id.ALBUMS,
	NavbarTab.Id.PLAYLISTS,
	NavbarTab.Id.ARTISTS,
	NavbarTab.Id.AUDIOBOOKS,
	NavbarTab.Id.ACTIVITY
)

private val audiobookProfileTabs = listOf(
	NavbarTab.Id.LIBRARY,
	NavbarTab.Id.AUDIOBOOKS,
	NavbarTab.Id.BOOKS,
	NavbarTab.Id.COLLECTIONS,
	NavbarTab.Id.AUTHORS,
	NavbarTab.Id.ACTIVITY
)

private val binderyTabIds = setOf(
	NavbarTab.Id.AUDIOBOOKS,
	NavbarTab.Id.BOOKS,
	NavbarTab.Id.COLLECTIONS,
	NavbarTab.Id.AUTHORS
)

fun navbarTabIdsForProfile(
	config: NavbarConfig,
	profile: BottomBarProfile,
	binderyEnabled: Boolean = true
): List<NavbarTab.Id> {
	val profileTabs = when (profile) {
		BottomBarProfile.Compact -> compactProfileTabs
		BottomBarProfile.Music -> musicProfileTabs
		BottomBarProfile.Audiobooks -> audiobookProfileTabs
	}
	val visible = config.tabs
		.filter { it.visible }
		.map { it.id }
		.toSet()
	return profileTabs.filter { tabId ->
		(tabId == NavbarTab.Id.LIBRARY || tabId in visible) &&
			(binderyEnabled || tabId !in binderyTabIds)
	}
}

fun bottomBarProfileForScreen(
	screen: Screen?,
	rememberedProfile: BottomBarProfile,
	binderyEnabled: Boolean = true
): BottomBarProfile =
	when (screen) {
		is Screen.Library,
		is Screen.AlbumList,
		is Screen.ArtistList,
		is Screen.GenreDetail,
		is Screen.GenreList,
		is Screen.PlaylistList,
		is Screen.RadioList,
		is Screen.SongList,
		is Screen.Starred -> BottomBarProfile.Music
		is Screen.Search -> when (screen.scope) {
			SearchScope.Music -> BottomBarProfile.Music
			SearchScope.Audiobooks -> if (binderyEnabled) BottomBarProfile.Audiobooks else BottomBarProfile.Music
		}

		Screen.Audiobooks,
		Screen.BinderyBooks,
		Screen.BinderyCollections,
		Screen.BinderyAuthors,
		is Screen.BinderyAuthor,
		is Screen.BinderyCollection,
		is Screen.BinderyCatalog -> if (binderyEnabled) BottomBarProfile.Audiobooks else rememberedProfile

		else -> rememberedProfile
	}

fun searchScopeForScreen(
	screen: Screen?,
	binderyEnabled: Boolean = true
): SearchScope =
	if (binderyEnabled) {
		when (screen) {
			Screen.Audiobooks,
			Screen.BinderyBooks,
			Screen.BinderyCollections,
			Screen.BinderyAuthors,
			is Screen.BinderyAuthor,
			is Screen.BinderyCollection,
			is Screen.BinderyCatalog -> SearchScope.Audiobooks
			is Screen.Search -> screen.scope
			else -> SearchScope.Music
		}
	} else {
		SearchScope.Music
	}

fun shouldUseSelectedTabIconFallbackMotion(destination: Screen): Boolean =
	when (destination) {
		Screen.Audiobooks,
		Screen.BinderyBooks,
		Screen.BinderyCollections,
		Screen.BinderyAuthors -> true

		else -> false
	}

fun bottomBarProfileForTabClick(
	tabId: NavbarTab.Id,
	currentProfile: BottomBarProfile
): BottomBarProfile =
	when (tabId) {
		NavbarTab.Id.LIBRARY,
		NavbarTab.Id.ALBUMS,
		NavbarTab.Id.PLAYLISTS,
		NavbarTab.Id.ARTISTS,
		NavbarTab.Id.SEARCH,
		NavbarTab.Id.GENRES,
		NavbarTab.Id.SONGS,
		NavbarTab.Id.RADIOS -> BottomBarProfile.Music

		NavbarTab.Id.AUDIOBOOKS,
		NavbarTab.Id.BOOKS,
		NavbarTab.Id.COLLECTIONS,
		NavbarTab.Id.AUTHORS -> BottomBarProfile.Audiobooks

		NavbarTab.Id.ACTIVITY -> currentProfile
	}
