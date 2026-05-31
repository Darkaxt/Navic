package paige.navic.domain.models.settings

import kotlinx.serialization.Serializable

@Serializable
data class NavbarTab(
	val id: Id,
	val visible: Boolean
) {
	@Serializable
	enum class Id {
		LIBRARY,
		ALBUMS,
		PLAYLISTS,
		ARTISTS,
		ACTIVITY,
		SEARCH,
		GENRES,
		SONGS,
		RADIOS
	}
}
