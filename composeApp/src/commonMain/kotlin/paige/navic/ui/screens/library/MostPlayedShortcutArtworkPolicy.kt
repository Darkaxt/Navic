package paige.navic.ui.screens.library

import androidx.compose.runtime.Immutable
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType
import paige.navic.domain.models.settings.ArtworkSourcePriority

@Immutable
data class MostPlayedShortcutArtistArtwork(
	val id: String,
	val name: String,
	val coverArtId: String?,
	val artistImageUrl: String?,
	val trustedExternalPhoto: Boolean = false
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
	songs: List<MostPlayedShortcutSongArtwork> = emptyList(),
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	aurralArtworkEnabled: Boolean = true
): List<DomainMostPlayedShortcut> =
	shortcuts.map { shortcut ->
		if (shortcut.type != PlaybackOriginType.Artist) {
			shortcut.copy(coverArtId = shortcut.coverArtId.cleanArtworkValue())
		} else {
			val nativeArtistCover = artists.artistCoverArtIdFor(shortcut)
			val trustedExternalPhoto = if (aurralArtworkEnabled) {
				artists.trustedArtistImageUrlFor(shortcut)
			} else {
				null
			}
			val localSongCover = songs.songArtworkFor(shortcut)
			val localAlbumCover = albums.albumArtworkFor(shortcut)
			val localSnapshotCover = shortcut.coverArtId.cleanArtworkValue()
				?.takeUnless { it.isAbsoluteHttpUrl() }
			shortcut.copy(
				coverArtId = when (artistArtworkPriority) {
					ArtworkSourcePriority.AurralFirst ->
						trustedExternalPhoto
							?: nativeArtistCover
							?: localSongCover
							?: localAlbumCover
							?: localSnapshotCover

					ArtworkSourcePriority.NativeFirst ->
						nativeArtistCover
							?: trustedExternalPhoto
							?: localSongCover
							?: localAlbumCover
							?: localSnapshotCover

					ArtworkSourcePriority.NativeOnly ->
						nativeArtistCover
							?: localSongCover
							?: localAlbumCover
							?: localSnapshotCover
				}
			)
		}
	}

private fun DomainMostPlayedShortcut.normalizedArtistId(): String? =
	id.normalizedArtworkMatchKey()

private fun List<MostPlayedShortcutArtistArtwork>.trustedArtistImageUrlFor(
	shortcut: DomainMostPlayedShortcut
): String? =
	firstNotNullOfOrNull { artist ->
		artist.artistImageUrl.cleanArtworkValue()
			?.takeIf {
				artist.trustedExternalPhoto &&
					it.isAbsoluteHttpUrl() &&
					artist.matches(shortcut)
			}
	}

fun mostPlayedArtistArtworkForShortcut(
	shortcut: DomainMostPlayedShortcut,
	candidates: List<MostPlayedShortcutArtistArtwork>
): MostPlayedShortcutArtistArtwork? =
	candidates.firstOrNull { artist ->
		artist.trustedExternalPhoto &&
		artist.artistImageUrl.cleanArtworkValue()?.isAbsoluteHttpUrl() == true &&
			artist.matches(shortcut)
	}

private fun List<MostPlayedShortcutArtistArtwork>.artistCoverArtIdFor(
	shortcut: DomainMostPlayedShortcut
): String? =
	firstNotNullOfOrNull { artist ->
		artist.coverArtId.cleanArtworkValue()
			?.takeUnless { it.isAbsoluteHttpUrl() }
			?.takeIf { artist.matches(shortcut) }
	}

private fun List<MostPlayedShortcutAlbumArtwork>.albumArtworkFor(
	shortcut: DomainMostPlayedShortcut
): String? =
	asSequence()
		.filter { album ->
			album.matches(shortcut)
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
			song.matches(shortcut)
		}
		.sortedWith(
			compareByDescending<MostPlayedShortcutSongArtwork> { it.playCount }
				.thenByDescending { it.year ?: Int.MIN_VALUE }
				.thenBy { it.albumTitle.orEmpty().lowercase() }
				.thenBy { it.title.lowercase() }
		)
		.firstNotNullOfOrNull { song -> song.coverArtId.cleanArtworkValue() }

private fun MostPlayedShortcutArtistArtwork.matches(shortcut: DomainMostPlayedShortcut): Boolean =
	id.normalizedArtworkMatchKey()?.let { it == shortcut.normalizedArtistId() } == true ||
		artworkNamesMatch(name, shortcut.title)

private fun MostPlayedShortcutAlbumArtwork.matches(shortcut: DomainMostPlayedShortcut): Boolean =
	artistId.normalizedArtworkMatchKey()?.let { it == shortcut.normalizedArtistId() } == true ||
		artworkNamesMatch(artistName, shortcut.title)

private fun MostPlayedShortcutSongArtwork.matches(shortcut: DomainMostPlayedShortcut): Boolean =
	artistId.normalizedArtworkMatchKey()?.let { it == shortcut.normalizedArtistId() } == true ||
		artworkNamesMatch(artistName, shortcut.title)

private fun String?.cleanArtworkValue(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.normalizedArtworkMatchKey(): String? =
	this
		?.trim()
		?.lowercase()
		?.takeIf { it.isNotEmpty() }

private fun String?.normalizedArtworkMatchName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

private fun artworkNamesMatch(
	candidateName: String?,
	shortcutName: String
): Boolean {
	val candidate = candidateName.normalizedArtworkMatchName() ?: return false
	val shortcut = shortcutName.normalizedArtworkMatchName() ?: return false
	if (candidate == shortcut) return true
	if (shortcut.length in 2..3 && (
			candidate.startsWith(shortcut) ||
				candidate.contains(" $shortcut") ||
				candidate.contains("($shortcut") ||
				candidate.contains("/$shortcut") ||
				candidate.contains(",$shortcut") ||
				candidate.contains(", $shortcut")
			)
	) {
		return true
	}

	val candidateCompact = candidate.compactArtworkName()
	val shortcutCompact = shortcut.compactArtworkName()
	if (candidateCompact.isNotEmpty() && candidateCompact == shortcutCompact) return true
	val candidateWordText = candidate.artworkWordText()
	val shortcutWordText = shortcut.artworkWordText()
	if (shortcutWordText.isNotEmpty() && " $candidateWordText ".contains(" $shortcutWordText ")) {
		return true
	}

	val shortcutWords = shortcut.artworkNameWords()
	if (shortcutWords.isEmpty()) return false
	val candidateWords = candidate.artworkNameWords()
	return shortcutWords.all { it in candidateWords }
}

private fun String.compactArtworkName(): String =
	buildString {
		for (index in indices) {
			val char = this@compactArtworkName[index]
			if (char.isArtworkNameCharacter()) append(char)
		}
	}

private fun String.artworkWordText(): String =
	buildString {
		var previousWasSeparator = true
		for (index in indices) {
			val char = this@artworkWordText[index]
			if (char.isArtworkNameCharacter()) {
				append(char)
				previousWasSeparator = false
			} else if (!previousWasSeparator) {
				append(' ')
				previousWasSeparator = true
			}
		}
	}.trim()

private fun String.artworkNameWords(): Set<String> =
	buildSet {
		val current = StringBuilder()
		for (index in indices) {
			val char = this@artworkNameWords[index]
			if (char.isArtworkNameCharacter()) {
				current.append(char)
			} else if (current.isNotEmpty()) {
				add(current.toString())
				current.clear()
			}
		}
		if (current.isNotEmpty()) add(current.toString())
	}

private fun Char.isArtworkNameCharacter(): Boolean =
	this in '0'..'9' || this in 'a'..'z' || this in 'A'..'Z' || code >= 128

private fun String.isAbsoluteHttpUrl(): Boolean =
	startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
