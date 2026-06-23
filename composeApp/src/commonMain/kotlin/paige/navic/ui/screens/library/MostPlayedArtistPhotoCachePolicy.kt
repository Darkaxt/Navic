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
	val imageUrl: String,
	val source: String = "Aurral",
	val updatedAtMillis: Long = 0L
)

fun mostPlayedArtistPhotoCacheArtworkForShortcut(
	shortcut: DomainMostPlayedShortcut,
	entries: List<MostPlayedArtistPhotoCacheEntry>
): MostPlayedShortcutArtistArtwork? {
	if (shortcut.type != PlaybackOriginType.Artist) return null
	return entries
		.asSequence()
		.filter { entry -> entry.imageUrl.isExternalArtistPhotoUrl() }
		.mapNotNull { entry -> entry.matchScore(shortcut)?.let { score -> score to entry } }
		.sortedWith(
			compareBy<Pair<MostPlayedArtistPhotoCacheMatchScore, MostPlayedArtistPhotoCacheEntry>> { it.first.matchRank }
				.thenBy { it.first.sourceRank }
				.thenByDescending { it.second.updatedAtMillis }
		)
		.firstOrNull()
		?.second
		?.let { entry ->
			MostPlayedShortcutArtistArtwork(
				id = entry.artistId?.takeIf { it.isNotBlank() }
					?: entry.sourceArtistId?.takeIf { it.isNotBlank() }
					?: entry.normalizedName,
				name = entry.name,
				coverArtId = null,
				artistImageUrl = entry.imageUrl,
				trustedExternalPhoto = true
			)
		}
}

fun ArtistPhotoCacheEntity.toMostPlayedArtistPhotoCacheEntry(): MostPlayedArtistPhotoCacheEntry =
	MostPlayedArtistPhotoCacheEntry(
		artistId = artistId,
		sourceArtistId = sourceArtistId,
		name = name,
		normalizedName = normalizedName,
		imageUrl = imageUrl,
		source = source,
		updatedAtMillis = updatedAtMillis
	)

fun mostPlayedArtistPhotoCacheEntity(
	shortcut: DomainMostPlayedShortcut,
	artist: MostPlayedShortcutArtistArtwork,
	nowMillis: Long,
	source: String = "Aurral"
): ArtistPhotoCacheEntity? {
	if (shortcut.type != PlaybackOriginType.Artist) return null
	val imageUrl = artist.artistImageUrl?.trim()?.takeIf { it.isExternalArtistPhotoUrl() } ?: return null
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

private data class MostPlayedArtistPhotoCacheMatchScore(
	val matchRank: Int,
	val sourceRank: Int
)

private fun MostPlayedArtistPhotoCacheEntry.matchScore(shortcut: DomainMostPlayedShortcut): MostPlayedArtistPhotoCacheMatchScore? {
	val shortcutId = shortcut.id.normalizedArtistPhotoCacheId()
	val shortcutName = shortcut.title.normalizedArtistPhotoCacheName()
	val matchRank = when {
		artistId.normalizedArtistPhotoCacheId()?.let { it == shortcutId } == true -> 0
		sourceArtistId.normalizedArtistPhotoCacheId()?.let { it == shortcutId } == true -> 1
		normalizedName.normalizedArtistPhotoCacheName()?.let { it == shortcutName } == true -> 2
		name.normalizedArtistPhotoCacheName()?.let { it == shortcutName } == true -> 3
		else -> null
	} ?: return null
	return MostPlayedArtistPhotoCacheMatchScore(
		matchRank = matchRank,
		sourceRank = source.artistPhotoCacheSourceRank()
	)
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

private fun String.isExternalArtistPhotoUrl(): Boolean =
	isAbsoluteHttpUrl() && !isNavidromeArtworkUrl()

private fun String.isNavidromeArtworkUrl(): Boolean {
	val normalized = lowercase()
	return "navidrome" in normalized ||
		"/rest/getcoverart" in normalized ||
		"/rest/getartistimage" in normalized ||
		"/getcoverart" in normalized ||
		"/getartistimage" in normalized
}

private fun String.artistPhotoCacheSourceRank(): Int =
	when (trim().lowercase()) {
		"aurral" -> 0
		"lastfm", "last.fm" -> 1
		else -> 2
	}
