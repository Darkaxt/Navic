package paige.navic.ui.screens.library

import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType
import paige.navic.ui.navigation.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MostPlayedShortcutNavigationPolicyTest {
	@Test
	fun shortcutsRouteToNativeDestinations() {
		assertEquals(
			Screen.ArtistDetail("artist"),
			mostPlayedShortcutDestination(shortcut(PlaybackOriginType.Artist, "artist"))
		)
		assertEquals(
			Screen.GenreDetail("genre"),
			mostPlayedShortcutDestination(shortcut(PlaybackOriginType.Genre, "genre"))
		)
		assertEquals(
			Screen.CollectionDetail("album", "MostPlayed"),
			mostPlayedShortcutDestination(shortcut(PlaybackOriginType.Album, "album"))
		)
		assertEquals(
			Screen.CollectionDetail("playlist", "MostPlayed"),
			mostPlayedShortcutDestination(shortcut(PlaybackOriginType.Playlist, "playlist"))
		)
		assertEquals(
			Screen.CollectionDetail("station", "MostPlayed"),
			mostPlayedShortcutDestination(shortcut(PlaybackOriginType.Station, "station"))
		)
	}

	private fun shortcut(
		type: PlaybackOriginType,
		id: String
	) = DomainMostPlayedShortcut(
		type = type,
		id = id,
		title = id,
		subtitle = null,
		coverArtId = null,
		totalPlayedMillis = 1_000L,
		lastPlayedAt = Instant.DISTANT_PAST
	)
}
