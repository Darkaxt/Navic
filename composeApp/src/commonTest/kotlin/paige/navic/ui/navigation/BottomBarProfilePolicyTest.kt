package paige.navic.ui.navigation

import paige.navic.domain.models.settings.BottomBarProfile
import paige.navic.domain.models.settings.NavbarConfig
import paige.navic.domain.models.settings.NavbarTab
import kotlin.test.Test
import kotlin.test.assertEquals

class BottomBarProfilePolicyTest {
	@Test
	fun compactProfileKeepsOnlyRootShortcuts() {
		assertEquals(
			listOf(
				NavbarTab.Id.LIBRARY,
				NavbarTab.Id.AUDIOBOOKS,
				NavbarTab.Id.ACTIVITY
			),
			navbarTabIdsForProfile(NavbarConfig.default, BottomBarProfile.Compact)
		)
	}

	@Test
	fun musicProfileIncludesAudiobooksAsCrossDomainShortcut() {
		assertEquals(
			listOf(
				NavbarTab.Id.LIBRARY,
				NavbarTab.Id.ALBUMS,
				NavbarTab.Id.PLAYLISTS,
				NavbarTab.Id.ARTISTS,
				NavbarTab.Id.AUDIOBOOKS,
				NavbarTab.Id.ACTIVITY
			),
			navbarTabIdsForProfile(NavbarConfig.default, BottomBarProfile.Music)
		)
	}

	@Test
	fun audiobookProfileShowsBooksCollectionsAndAuthors() {
		assertEquals(
			listOf(
				NavbarTab.Id.LIBRARY,
				NavbarTab.Id.AUDIOBOOKS,
				NavbarTab.Id.BOOKS,
				NavbarTab.Id.COLLECTIONS,
				NavbarTab.Id.AUTHORS,
				NavbarTab.Id.ACTIVITY
			),
			navbarTabIdsForProfile(NavbarConfig.default, BottomBarProfile.Audiobooks)
		)
	}

	@Test
	fun disabledBinderyRemovesAudiobookTabsFromEveryProfile() {
		assertEquals(
			listOf(
				NavbarTab.Id.LIBRARY,
				NavbarTab.Id.ACTIVITY
			),
			navbarTabIdsForProfile(
				config = NavbarConfig.default,
				profile = BottomBarProfile.Compact,
				binderyEnabled = false
			)
		)
		assertEquals(
			listOf(
				NavbarTab.Id.LIBRARY,
				NavbarTab.Id.ALBUMS,
				NavbarTab.Id.PLAYLISTS,
				NavbarTab.Id.ARTISTS,
				NavbarTab.Id.ACTIVITY
			),
			navbarTabIdsForProfile(
				config = NavbarConfig.default,
				profile = BottomBarProfile.Music,
				binderyEnabled = false
			)
		)
		assertEquals(
			listOf(
				NavbarTab.Id.LIBRARY,
				NavbarTab.Id.ACTIVITY
			),
			navbarTabIdsForProfile(
				config = NavbarConfig.default,
				profile = BottomBarProfile.Audiobooks,
				binderyEnabled = false
			)
		)
	}

	@Test
	fun screenDomainOverridesRememberedProfileForSpecificScreens() {
		assertEquals(
			BottomBarProfile.Music,
			bottomBarProfileForScreen(Screen.AlbumList(), BottomBarProfile.Compact)
		)
		assertEquals(
			BottomBarProfile.Audiobooks,
			bottomBarProfileForScreen(Screen.BinderyBooks, BottomBarProfile.Music)
		)
		assertEquals(
			BottomBarProfile.Audiobooks,
			bottomBarProfileForScreen(Screen.BinderyAuthors, BottomBarProfile.Compact)
		)
		assertEquals(
			BottomBarProfile.Audiobooks,
			bottomBarProfileForScreen(Screen.BinderyAuthor("/opds/authors/28", "Brandon Sanderson"), BottomBarProfile.Music)
		)
		assertEquals(
			BottomBarProfile.Audiobooks,
			bottomBarProfileForScreen(Screen.BinderyCollection("/opds/collections/5", "Alcatraz"), BottomBarProfile.Music)
		)
		assertEquals(
			BottomBarProfile.Audiobooks,
			bottomBarProfileForScreen(Screen.BinderyBook("3693", "Alcatraz"), BottomBarProfile.Music)
		)
		assertEquals(
			BottomBarProfile.Audiobooks,
			bottomBarProfileForScreen(Screen.BinderyFinding("/opds/findings/894", "The Hobbit.pdf"), BottomBarProfile.Music)
		)
		assertEquals(
			BottomBarProfile.Music,
			bottomBarProfileForScreen(Screen.Library(), BottomBarProfile.Music)
		)
		assertEquals(
			BottomBarProfile.Music,
			bottomBarProfileForScreen(Screen.Library(), BottomBarProfile.Compact)
		)
	}

	@Test
	fun disabledBinderyDoesNotForceAudiobookProfileForStaleAudiobookScreens() {
		assertEquals(
			BottomBarProfile.Music,
			bottomBarProfileForScreen(
				screen = Screen.Audiobooks,
				rememberedProfile = BottomBarProfile.Music,
				binderyEnabled = false
			)
		)
		assertEquals(
			BottomBarProfile.Compact,
			bottomBarProfileForScreen(
				screen = Screen.BinderyBooks,
				rememberedProfile = BottomBarProfile.Compact,
				binderyEnabled = false
			)
		)
		assertEquals(
			BottomBarProfile.Music,
			bottomBarProfileForScreen(
				screen = Screen.BinderyCollection("/opds/collections/5", "Alcatraz"),
				rememberedProfile = BottomBarProfile.Music,
				binderyEnabled = false
			)
		)
	}

	@Test
	fun searchScopeFollowsCurrentDomain() {
		assertEquals(SearchScope.Music, searchScopeForScreen(Screen.Library()))
		assertEquals(SearchScope.Music, searchScopeForScreen(Screen.ArtistList()))
		assertEquals(SearchScope.Audiobooks, searchScopeForScreen(Screen.Audiobooks))
		assertEquals(SearchScope.Audiobooks, searchScopeForScreen(Screen.BinderyBooks))
		assertEquals(
			SearchScope.Audiobooks,
			searchScopeForScreen(Screen.BinderyAuthor("/opds/authors/28", "Brandon Sanderson"))
		)
		assertEquals(
			SearchScope.Audiobooks,
			searchScopeForScreen(Screen.BinderyBook("3693", "Alcatraz"))
		)
		assertEquals(
			SearchScope.Audiobooks,
			searchScopeForScreen(Screen.BinderyFinding("/opds/findings/894", "The Hobbit.pdf"))
		)
		assertEquals(
			SearchScope.Music,
			searchScopeForScreen(Screen.BinderyBooks, binderyEnabled = false)
		)
	}

	@Test
	fun tabClickSelectsTheNextProfile() {
		assertEquals(
			BottomBarProfile.Music,
			bottomBarProfileForTabClick(NavbarTab.Id.LIBRARY, BottomBarProfile.Compact)
		)
		assertEquals(
			BottomBarProfile.Audiobooks,
			bottomBarProfileForTabClick(NavbarTab.Id.AUDIOBOOKS, BottomBarProfile.Music)
		)
		assertEquals(
			BottomBarProfile.Audiobooks,
			bottomBarProfileForTabClick(NavbarTab.Id.BOOKS, BottomBarProfile.Compact)
		)
		assertEquals(
			BottomBarProfile.Music,
			bottomBarProfileForTabClick(NavbarTab.Id.ACTIVITY, BottomBarProfile.Music)
		)
	}

	@Test
	fun audiobookTabsUseFallbackSelectedIconMotion() {
		listOf(
			Screen.Audiobooks,
			Screen.BinderyBooks,
			Screen.BinderyCollections,
			Screen.BinderyAuthors
		).forEach { screen ->
			assertEquals(true, shouldUseSelectedTabIconFallbackMotion(screen), message = screen.toString())
		}

		listOf(
			Screen.Library(),
			Screen.PlaylistList(),
			Screen.ArtistList(),
			Screen.Activity
		).forEach { screen ->
			assertEquals(false, shouldUseSelectedTabIconFallbackMotion(screen), message = screen.toString())
		}
	}
}
