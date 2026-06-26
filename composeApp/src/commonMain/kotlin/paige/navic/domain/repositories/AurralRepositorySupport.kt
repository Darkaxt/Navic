package paige.navic.domain.repositories

import kotlinx.serialization.Serializable
import paige.navic.domain.repositories.AurralConfirmationType.ArtistMonitoring
import kotlin.time.Duration

internal data class AurralLibraryArtistsCacheEntry(
	val key: String,
	val artists: List<AurralDiscoverArtist>,
	val loadedAtMillis: Long
) {
	fun isFresh(nowMillis: Long, ttl: Duration = AURRAL_LIBRARY_ARTISTS_CACHE_TTL): Boolean =
		nowMillis >= loadedAtMillis &&
			nowMillis - loadedAtMillis < ttl.inWholeMilliseconds

	fun withMonitoring(
		artistMbid: String,
		artistName: String,
		monitored: Boolean,
		loadedAtMillis: Long = this.loadedAtMillis
	): AurralLibraryArtistsCacheEntry {
		val updated = artists.updateAurralLibraryArtistMonitoring(
			artistMbid = artistMbid,
			artistName = artistName,
			monitored = monitored
		)
		return copy(artists = updated, loadedAtMillis = loadedAtMillis)
	}
}

internal data class ResolvedAurralArtist(
	val artistMbid: String,
	val artistName: String
)

@Serializable
internal data class AurralCachedString(
	val value: String? = null
)

internal fun aurralLibraryArtistsCacheKey(
	baseUrl: String,
	requestHeaders: Map<String, String>
): String =
	buildString {
		append(baseUrl.trimEnd('/'))
		requestHeaders.entries.sortedBy { entry -> entry.key }.forEach { (key, value) ->
			append('|')
			append(key.lowercase())
			append('=')
			append(value.hashCode())
		}
	}

internal fun aurralSearchCachePath(
	query: String,
	limit: Int,
	offset: Int
): String =
	listOf(
		"query=${query.normalizedAurralSearchName().orEmpty()}",
		"limit=${limit.coerceAtLeast(1)}",
		"offset=${offset.coerceAtLeast(0)}"
	).joinToString("|")

internal fun aurralAlbumTracksCachePath(
	releaseGroupMbid: String,
	libraryAlbumId: String?
): String =
	listOf(
		"releaseGroup=${releaseGroupMbid.normalizedAurralCacheKey().orEmpty()}",
		"libraryAlbum=${libraryAlbumId.normalizedAurralCacheKey().orEmpty()}"
	).joinToString("|")

internal fun aurralArtistEnrichmentCachePath(
	artistMbid: String,
	artistName: String
): String =
	listOf(
		"artist=${artistMbid.normalizedAurralCacheKey().orEmpty()}",
		"name=${artistName.normalizedAurralSearchName().orEmpty()}"
	).joinToString("|")

internal fun aurralArtistSectionCachePath(
	artistMbid: String,
	artistName: String,
	section: String
): String =
	listOf(
		aurralArtistEnrichmentCachePath(artistMbid, artistName),
		"section=${section.trim().lowercase()}"
	).joinToString("|")

internal fun aurralReleaseGroupCoverCachePath(
	releaseGroupMbid: String,
	artistName: String,
	albumTitle: String
): String =
	listOf(
		"releaseGroup=${releaseGroupMbid.normalizedAurralCacheKey().orEmpty()}",
		"artist=${artistName.normalizedAurralSearchName().orEmpty()}",
		"album=${albumTitle.normalizedAurralSearchName().orEmpty()}"
	).joinToString("|")

internal fun aurralArtistMonitoringConfirmationId(artistMbid: String): String =
	"artist-monitor:${artistMbid.normalizedAurralCacheKey() ?: artistMbid.trim()}"

fun aurralArtistMonitoringConfirmationItem(
	queue: List<AurralConfirmationQueueItem>,
	artistMbid: String?
): AurralConfirmationQueueItem? {
	val normalizedMbid = artistMbid.normalizedAurralCacheKey() ?: return null
	val confirmationId = aurralArtistMonitoringConfirmationId(normalizedMbid)
	return queue.lastOrNull { item ->
		item.type == ArtistMonitoring &&
			(item.id == confirmationId || item.artistMbid.normalizedAurralCacheKey() == normalizedMbid)
	}
}

internal fun List<AurralDiscoverArtist>.findAurralLibraryArtist(
	artistMbid: String,
	artistName: String
): AurralDiscoverArtist? {
	val artistKey = artistMbid.normalizedAurralCacheKey()
	val artistNameKey = artistName.normalizedAurralImageLookupName()
	return firstOrNull { artist ->
		(artistKey != null && artist.id.normalizedAurralCacheKey() == artistKey) ||
			(artistNameKey != null && artist.name.normalizedAurralImageLookupName() == artistNameKey)
	}
}

internal fun String?.normalizedAurralSearchName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

internal fun List<AurralDiscoverArtist>.updateAurralLibraryArtistMonitoring(
	artistMbid: String,
	artistName: String,
	monitored: Boolean
): List<AurralDiscoverArtist> {
	var matched = false
	val updated = map { artist ->
		if (artist.matchesAurralLibraryArtist(artistMbid, artistName)) {
			matched = true
			artist.copy(monitored = monitored)
		} else {
			artist
		}
	}
	return if (matched) {
		updated
	} else {
		updated + AurralDiscoverArtist(
			id = artistMbid,
			name = artistName,
			monitored = monitored
		)
	}
}

internal fun AurralDiscoverArtist.matchesAurralLibraryArtist(
	artistMbid: String,
	artistName: String
): Boolean {
	val artistKey = artistMbid.normalizedAurralCacheKey()
	val artistNameKey = artistName.normalizedAurralImageLookupName()
	return (artistKey != null && id.normalizedAurralCacheKey() == artistKey) ||
		(artistNameKey != null && name.normalizedAurralImageLookupName() == artistNameKey)
}

internal fun String?.normalizedAurralCacheKey(): String? =
	this?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

internal fun String?.normalizedAurralImageLookupName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }
