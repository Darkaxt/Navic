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

@Immutable
data class MostPlayedShortcutSongArtwork(
	val artistId: String?,
	val artistName: String?,
	val coverArtId: String?,
	val year: Int?,
	val albumTitle: String?,
	val title: String,
	val playCount: Int
)

fun mostPlayedShortcutsWithResolvedArtwork(
	shortcuts: List<DomainMostPlayedShortcut>,
	artists: List<MostPlayedShortcutArtistArtwork>,
	albums: List<MostPlayedShortcutAlbumArtwork>,
	songs: List<MostPlayedShortcutSongArtwork> = emptyList()
): List<DomainMostPlayedShortcut> =
	shortcuts.map { shortcut ->
		if (shortcut.type != PlaybackOriginType.Artist) {
			shortcut.copy(coverArtId = shortcut.coverArtId.cleanArtworkValue())
		} else {
			shortcut.copy(
				coverArtId = shortcut.coverArtId.cleanArtworkValue()?.takeIf { it.isAbsoluteHttpUrl() }
					?: artists.artistImageUrlFor(shortcut)
					?: albums.albumArtworkFor(shortcut)
					?: songs.songArtworkFor(shortcut)
					?: artists.artistCoverArtIdFor(shortcut)
					?: shortcut.coverArtId.cleanArtworkValue()
			)
		}
	}

private fun List<MostPlayedShortcutArtistArtwork>.artistImageUrlFor(
	shortcut: DomainMostPlayedShortcut
): String? =
	firstOrNull { artist ->
		artist.id == shortcut.id || artist.name.equals(shortcut.title, ignoreCase = true)
	}?.artistImageUrl.cleanArtworkValue()

private fun List<MostPlayedShortcutArtistArtwork>.artistCoverArtIdFor(
	shortcut: DomainMostPlayedShortcut
): String? =
	firstOrNull { artist ->
		artist.id == shortcut.id || artist.name.equals(shortcut.title, ignoreCase = true)
	}?.coverArtId.cleanArtworkValue()

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

private fun List<MostPlayedShortcutSongArtwork>.songArtworkFor(
	shortcut: DomainMostPlayedShortcut
): String? =
	asSequence()
		.filter { song ->
			song.artistId == shortcut.id ||
				song.artistName.equals(shortcut.title, ignoreCase = true)
		}
		.sortedWith(
			compareByDescending<MostPlayedShortcutSongArtwork> { it.playCount }
				.thenByDescending { it.year ?: Int.MIN_VALUE }
				.thenBy { it.albumTitle.orEmpty().lowercase() }
				.thenBy { it.title.lowercase() }
		)
		.firstNotNullOfOrNull { song -> song.coverArtId.cleanArtworkValue() }

private fun String?.cleanArtworkValue(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }

private fun String.isAbsoluteHttpUrl(): Boolean =
	startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
