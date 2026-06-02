package paige.navic.domain.models.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavbarConfigMigrationTest {
	@Test
	fun defaultConfigIncludesMusicAndAudiobookTabs() {
		assertEquals(
			listOf(
				NavbarTab.Id.LIBRARY,
				NavbarTab.Id.ALBUMS,
				NavbarTab.Id.PLAYLISTS,
				NavbarTab.Id.ARTISTS,
				NavbarTab.Id.AUDIOBOOKS,
				NavbarTab.Id.ACTIVITY,
				NavbarTab.Id.BOOKS,
				NavbarTab.Id.COLLECTIONS,
				NavbarTab.Id.AUTHORS
			),
			NavbarConfig.default.tabs.filter { it.visible }.map { it.id }
		)
	}

	@Test
	fun migrationPreservesCustomOrderAndVisibilityWhenAddingActivity() {
		val migrated = migrateNavbarConfig(
			NavbarConfig(
				version = 7,
				tabs = listOf(
					NavbarTab(NavbarTab.Id.LIBRARY, true),
					NavbarTab(NavbarTab.Id.SEARCH, true),
					NavbarTab(NavbarTab.Id.ARTISTS, true),
					NavbarTab(NavbarTab.Id.ALBUMS, false)
				)
			)
		)

		assertEquals(NavbarConfig.VERSION, migrated.version)
		assertEquals(
			listOf(
				NavbarTab.Id.LIBRARY,
				NavbarTab.Id.SEARCH,
				NavbarTab.Id.ARTISTS,
				NavbarTab.Id.AUDIOBOOKS,
				NavbarTab.Id.ACTIVITY,
				NavbarTab.Id.ALBUMS,
				NavbarTab.Id.PLAYLISTS,
				NavbarTab.Id.GENRES,
				NavbarTab.Id.SONGS,
				NavbarTab.Id.RADIOS,
				NavbarTab.Id.BOOKS,
				NavbarTab.Id.COLLECTIONS,
				NavbarTab.Id.AUTHORS
			),
			migrated.tabs.map { it.id }
		)
		assertTrue(migrated.tabs.first { it.id == NavbarTab.Id.SEARCH }.visible)
		assertTrue(migrated.tabs.first { it.id == NavbarTab.Id.ACTIVITY }.visible)
		assertFalse(migrated.tabs.first { it.id == NavbarTab.Id.ALBUMS }.visible)
	}
}
