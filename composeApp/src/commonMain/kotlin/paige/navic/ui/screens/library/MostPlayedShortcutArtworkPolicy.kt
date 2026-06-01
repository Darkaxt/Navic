package paige.navic.ui.screens.library

import androidx.compose.runtime.Immutable
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType

@Immutable
data class MostPlayedShortcutArtistArtwork(
	val id: String,
	val name: String,
	val coverArtId: String?,
	val artistImageUrl: String?
)

@Immutable
data class MostPlayedShortcutAlbumArtwork(
	val artistId: String?,
	val artistName: String?,
	val coverArtId: String?,
	val year: Int?,
	val name: String
)

fun mostPlayedShortcutsWithResolvedArtwork(
	shortcuts: List<DomainMostPlayedShortcut>,
	artists: List<MostPlayedShortcutArtistArtwork>,
	albums: List<MostPlayedShortcutAlbumArtwork>
): List<DomainMostPlayedShortcut> =
	shortcuts.map { shortcut ->
		if (shortcut.type != PlaybackOriginType.Artist) {
			shortcut.copy(coverArtId = shortcut.coverArtId.cleanArtworkValue())
		} else {
			shortcut.copy(
				coverArtId = shortcut.coverArtId.cleanArtworkValue()
					?: artists.artistArtworkFor(shortcut)
					?: albums.albumArtworkFor(shortcut)
			)
		}
	}

private fun List<MostPlayedShortcutArtistArtwork>.artistArtworkFor(
	shortcut: DomainMostPlayedShortcut
): String? =
	firstOrNull { artist ->
		artist.id == shortcut.id || artist.name.equals(shortcut.title, ignoreCase = true)
	}?.let { artist ->
		artist.artistImageUrl.cleanArtworkValue()
			?: artist.coverArtId.cleanArtworkValue()
	}

private fun List<MostPlayedShortcutAlbumArtwork>.albumArtworkFor(
	shortcut: DomainMostPlayedShortcut
): String? =
	asSequence()
		.filter { album ->
			album.artistId == shortcut.id ||
				album.artistName.equals(shortcut.title, ignoreCase = true)
		}
		.sortedWith(
			compareByDescending<MostPlayedShortcutAlbumArtwork> { it.year ?: Int.MIN_VALUE }
				.thenBy { it.name.lowercase() }
		)
		.firstNotNullOfOrNull { album -> album.coverArtId.cleanArtworkValue() }

private fun String?.cleanArtworkValue(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }
