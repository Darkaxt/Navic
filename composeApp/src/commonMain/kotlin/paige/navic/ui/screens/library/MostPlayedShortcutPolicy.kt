package paige.navic.ui.screens.library

import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType
import paige.navic.ui.navigation.Screen

data class MostPlayedArtistLookupIdentities(
	val ids: List<String>,
	val normalizedNames: List<String>
)

fun mostPlayedArtistLookupIdentities(
	shortcuts: List<DomainMostPlayedShortcut>
): MostPlayedArtistLookupIdentities {
	val artistShortcuts = shortcuts.filter { it.type == PlaybackOriginType.Artist }
	return MostPlayedArtistLookupIdentities(
		ids = artistShortcuts
			.map { it.id.trim() }
			.filter { it.isNotEmpty() }
			.distinct(),
		normalizedNames = artistShortcuts
			.mapNotNull { it.title.normalizedShortcutEntityName() }
			.distinct()
	)
}

fun mostPlayedShortcutDestination(shortcut: DomainMostPlayedShortcut): Screen =
	when (shortcut.type) {
		PlaybackOriginType.Artist -> Screen.ArtistDetail(shortcut.id)
		PlaybackOriginType.Genre -> Screen.GenreDetail(shortcut.id)
		PlaybackOriginType.Album,
		PlaybackOriginType.Playlist,
		PlaybackOriginType.Station -> Screen.CollectionDetail(shortcut.id, "MostPlayed")
	}

fun mostPlayedShortcutsWithResolvedLocalArtists(
	shortcuts: List<DomainMostPlayedShortcut>,
	artists: List<MostPlayedShortcutArtistArtwork>
): List<DomainMostPlayedShortcut> =
	shortcuts.mapNotNull { shortcut ->
		if (shortcut.type != PlaybackOriginType.Artist) {
			shortcut
		} else {
			artists.firstOrNull { artist -> artist.matchesShortcutEntity(shortcut) }
				?.let { artist ->
					shortcut.copy(
						id = artist.id,
						title = artist.name
					)
				}
		}
	}

private fun MostPlayedShortcutArtistArtwork.matchesShortcutEntity(
	shortcut: DomainMostPlayedShortcut
): Boolean =
	id.normalizedShortcutEntityKey()?.let { it == shortcut.id.normalizedShortcutEntityKey() } == true ||
		name.normalizedShortcutEntityName()?.let { it == shortcut.title.normalizedShortcutEntityName() } == true

private fun String?.normalizedShortcutEntityKey(): String? =
	this
		?.trim()
		?.lowercase()
		?.takeIf { it.isNotEmpty() }

private fun String?.normalizedShortcutEntityName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }
