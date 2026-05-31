package paige.navic.ui.screens.library

import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType
import paige.navic.ui.navigation.Screen

fun mostPlayedShortcutDestination(shortcut: DomainMostPlayedShortcut): Screen =
	when (shortcut.type) {
		PlaybackOriginType.Artist -> Screen.ArtistDetail(shortcut.id)
		PlaybackOriginType.Genre -> Screen.GenreDetail(shortcut.id)
		PlaybackOriginType.Album,
		PlaybackOriginType.Playlist,
		PlaybackOriginType.Station -> Screen.CollectionDetail(shortcut.id, "MostPlayed")
	}
