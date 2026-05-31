package paige.navic.domain.models.settings

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class NavbarConfig(
	val tabs: List<NavbarTab>,
	val version: Int
) {
	companion object {
		const val KEY = "navbarConfig"
		const val VERSION = 8
		val default = NavbarConfig(
			tabs = listOf(
				NavbarTab(NavbarTab.Id.LIBRARY, true),
				NavbarTab(NavbarTab.Id.ALBUMS, true),
				NavbarTab(NavbarTab.Id.PLAYLISTS, true),
				NavbarTab(NavbarTab.Id.ARTISTS, true),
				NavbarTab(NavbarTab.Id.ACTIVITY, true),
				NavbarTab(NavbarTab.Id.SEARCH, false),
				NavbarTab(NavbarTab.Id.GENRES, false),
				NavbarTab(NavbarTab.Id.SONGS, false),
				NavbarTab(NavbarTab.Id.RADIOS, false)
			),
			version = VERSION
		)
	}
}

fun migrateNavbarConfig(config: NavbarConfig): NavbarConfig {
	val defaultTabsById = NavbarConfig.default.tabs.associateBy { it.id }
	val seen = mutableSetOf<NavbarTab.Id>()
	val migratedTabs = config.tabs
		.mapNotNull { tab ->
			val defaultTab = defaultTabsById[tab.id] ?: return@mapNotNull null
			if (!seen.add(tab.id)) return@mapNotNull null
			defaultTab.copy(visible = tab.visible)
		}
		.toMutableList()
	if (migratedTabs.isEmpty()) return NavbarConfig.default

	if (NavbarTab.Id.ACTIVITY !in seen) {
		val activityTab = defaultTabsById.getValue(NavbarTab.Id.ACTIVITY)
		val insertAfterIndex = migratedTabs.indexOfFirst { it.id == NavbarTab.Id.ARTISTS }
			.takeIf { it >= 0 }
			?: migratedTabs.indexOfFirst { it.id == NavbarTab.Id.PLAYLISTS }
				.takeIf { it >= 0 }
		if (insertAfterIndex == null) {
			migratedTabs.add(activityTab)
		} else {
			migratedTabs.add(insertAfterIndex + 1, activityTab)
		}
		seen += NavbarTab.Id.ACTIVITY
	}

	NavbarConfig.default.tabs.forEach { tab ->
		if (tab.id !in seen) {
			migratedTabs.add(tab)
			seen += tab.id
		}
	}

	return NavbarConfig(
		tabs = migratedTabs,
		version = NavbarConfig.VERSION
	)
}
