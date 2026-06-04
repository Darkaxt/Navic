package paige.navic.ui.screens.library

import androidx.compose.runtime.Immutable
import paige.navic.data.database.entities.ArtistPhotoCacheEntity
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.PlaybackOriginType

@Immutable
data class MostPlayedArtistPhotoCacheEntry(
	val artistId: String?,
	val sourceArtistId: String?,
	val name: String,
	val normalizedName: String,
	val imageUrl: String
)

fun mostPlayedArtistPhotoCacheArtworkForShortcut(
	shortcut: DomainMostPlayedShortcut,
	entries: List<MostPlayedArtistPhotoCacheEntry>
): MostPlayedShortcutArtistArtwork? {
	if (shortcut.type != PlaybackOriginType.Artist) return null
	return entries.firstOrNull { entry ->
		entry.imageUrl.isAbsoluteHttpUrl() && entry.matches(shortcut)
	}?.let { entry ->
		MostPlayedShortcutArtistArtwork(
			id = entry.artistId?.takeIf { it.isNotBlank() }
				?: entry.sourceArtistId?.takeIf { it.isNotBlank() }
				?: entry.normalizedName,
			name = entry.name,
			coverArtId = null,
			artistImageUrl = entry.imageUrl
		)
	}
}

fun ArtistPhotoCacheEntity.toMostPlayedArtistPhotoCacheEntry(): MostPlayedArtistPhotoCacheEntry =
	MostPlayedArtistPhotoCacheEntry(
		artistId = artistId,
		sourceArtistId = sourceArtistId,
		name = name,
		normalizedName = normalizedName,
		imageUrl = imageUrl
	)

fun mostPlayedArtistPhotoCacheEntity(
	shortcut: DomainMostPlayedShortcut,
	artist: MostPlayedShortcutArtistArtwork,
	nowMillis: Long,
	source: String = "Aurral"
): ArtistPhotoCacheEntity? {
	if (shortcut.type != PlaybackOriginType.Artist) return null
	val imageUrl = artist.artistImageUrl?.trim()?.takeIf { it.isAbsoluteHttpUrl() } ?: return null
	val normalizedName = listOf(shortcut.title, artist.name)
		.firstNotNullOfOrNull { it.normalizedArtistPhotoCacheName() }
		?: return null
	val artistId = shortcut.id.trim().takeIf { it.isNotEmpty() }
	val sourceArtistId = artist.id.trim().takeIf { it.isNotEmpty() }
	val cacheKey = artistId?.let { "artist:$it" }
		?: sourceArtistId?.let { "source:$it" }
		?: "name:$normalizedName"
	return ArtistPhotoCacheEntity(
		cacheKey = cacheKey,
		artistId = artistId,
		sourceArtistId = sourceArtistId,
		name = artist.name.trim().takeIf { it.isNotEmpty() } ?: shortcut.title,
		normalizedName = normalizedName,
		imageUrl = imageUrl,
		source = source,
		updatedAtMillis = nowMillis
	)
}

private fun MostPlayedArtistPhotoCacheEntry.matches(shortcut: DomainMostPlayedShortcut): Boolean {
	val shortcutId = shortcut.id.normalizedArtistPhotoCacheId()
	val shortcutName = shortcut.title.normalizedArtistPhotoCacheName()
	return artistId.normalizedArtistPhotoCacheId()?.let { it == shortcutId } == true ||
		sourceArtistId.normalizedArtistPhotoCacheId()?.let { it == shortcutId } == true ||
		normalizedName.normalizedArtistPhotoCacheName()?.let { it == shortcutName } == true ||
		name.normalizedArtistPhotoCacheName()?.let { it == shortcutName } == true
}

private fun String?.normalizedArtistPhotoCacheId(): String? =
	this?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

private fun String?.normalizedArtistPhotoCacheName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

private fun String.isAbsoluteHttpUrl(): Boolean =
	startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
