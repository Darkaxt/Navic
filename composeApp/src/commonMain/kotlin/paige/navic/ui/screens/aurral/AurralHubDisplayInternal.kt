package paige.navic.ui.screens.aurral

import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.isNavidromeArtworkUrl
import paige.navic.domain.models.settings.ArtworkSourcePriority
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.ui.screens.artist.ArtistHeaderImageCacheEntry
import paige.navic.ui.screens.artist.artistDetailCachedImageUrl
import paige.navic.ui.screens.artist.artistHeaderImageCacheIndex

internal fun AurralAlbumSearchItem.toDiscoverArtistRecommendation(): AurralDiscoverArtist? {
	val artistMbid = artistMbid.trim().takeIf { it.isNotEmpty() } ?: return null
	val artistName = artistName.trim().takeIf { it.isNotEmpty() } ?: return null
	return AurralDiscoverArtist(
		id = artistMbid,
		name = artistName,
		reason = "Recommended: ${title.trim()}",
		recommendedAlbums = listOf(this)
	)
}

internal fun mergeAurralDiscoverArtists(
	artists: List<AurralDiscoverArtist>
): List<AurralDiscoverArtist> {
	val merged = linkedMapOf<String, AurralDiscoverArtist>()
	artists.forEach { artist ->
		val key = artist.id.normalizedAurralKey() ?: return@forEach
		val safeArtist = artist.copy(
			id = artist.id.trim(),
			name = artist.name.trim(),
			imageUrl = artist.imageUrl.externalAurralArtworkUrlOrNull(),
			recommendedAlbums = artist.recommendedAlbums.distinctBy { it.id.trim().lowercase() }
		)
		val existing = merged[key]
		merged[key] = if (existing == null) {
			safeArtist
		} else {
			existing.copy(
				imageUrl = preferredExternalArtworkUrl(existing.imageUrl, safeArtist.imageUrl),
				tags = (existing.tags + safeArtist.tags).distinctBy { it.trim().lowercase() },
				matchedTags = (existing.matchedTags + safeArtist.matchedTags)
					.distinctBy { it.trim().lowercase() },
				reason = existing.reason ?: safeArtist.reason,
				sourceType = existing.sourceType ?: safeArtist.sourceType,
				discoveryTier = existing.discoveryTier ?: safeArtist.discoveryTier,
				monitored = existing.monitored ?: safeArtist.monitored,
				recommendedAlbums = (existing.recommendedAlbums + safeArtist.recommendedAlbums)
					.distinctBy { it.id.trim().lowercase() }
			)
		}
	}
	return merged.values.toList()
}

internal fun List<AurralDiscoverArtist>.withLibraryArtistMonitoring(
	libraryArtists: List<AurralDiscoverArtist>
): List<AurralDiscoverArtist> {
	if (libraryArtists.isEmpty()) return this
	val libraryById = libraryArtists
		.mapNotNull { artist -> artist.id.normalizedAurralKey()?.let { it to artist } }
		.toMap()
	val libraryByName = libraryArtists
		.mapNotNull { artist -> artist.name.normalizedAurralName()?.let { it to artist } }
		.toMap()
	return map { artist ->
		val libraryArtist = artist.id.normalizedAurralKey()?.let(libraryById::get)
			?: artist.name.normalizedAurralName()?.let(libraryByName::get)
		artist.copy(
			imageUrl = preferredExternalArtworkUrl(
				primary = artist.imageUrl,
				fallback = libraryArtist?.imageUrl
			),
			monitored = libraryArtist?.monitored ?: artist.monitored ?: false
		)
	}
}
internal fun List<AurralDiscoverArtist>.withCachedArtistPhotos(
	entries: List<ArtistHeaderImageCacheEntry>,
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): List<AurralDiscoverArtist> =
	if (entries.isEmpty()) {
		this
	} else {
		val index = artistHeaderImageCacheIndex(entries)
		map { artist ->
			val cachedImageUrl = artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = artist.id,
					name = artist.name,
					musicBrainzId = artist.id,
					coverArtId = null
				),
				index = index,
				artistArtworkPriority = artistArtworkPriority,
				externalArtworkEnabled = externalArtworkEnabled
			)
			if (cachedImageUrl.isNullOrBlank()) {
				artist
			} else {
				artist.copy(imageUrl = cachedImageUrl)
			}
		}
	}

internal fun String?.normalizedAurralKey(): String? =
	this
		?.trim()
		?.lowercase()
		?.takeIf { it.isNotEmpty() }

internal fun String?.normalizedAurralName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

private fun preferredExternalArtworkUrl(
	primary: String?,
	fallback: String?
): String? {
	val primaryUrl = primary.externalAurralArtworkUrlOrNull()
	val fallbackUrl = fallback.externalAurralArtworkUrlOrNull()
	return when {
		primaryUrl == null -> fallbackUrl
		fallbackUrl == null -> primaryUrl
		else -> primaryUrl
	}
}

internal fun String?.externalAurralArtworkUrlOrNull(): String? {
	val url = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	return url.takeUnless { it.isNavidromeArtworkUrl() }
}

internal fun String?.aurralSearchYearOrNull(): Int? =
	this
		?.trim()
		?.take(4)
		?.takeIf { value -> value.length == 4 && value.all { it.isDigit() } }
		?.toIntOrNull()
