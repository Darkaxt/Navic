package paige.navic.domain.models

import androidx.compose.runtime.Immutable
import paige.navic.domain.models.settings.ArtworkSourcePriority

@Immutable
data class PlaybackArtworkResolution(
	val coverArtId: String?,
	val imageUrl: String?,
	val imageCacheKey: String?,
	val source: PlaybackArtworkSource
) {
	val hasArtwork: Boolean
		get() = !coverArtId.isNullOrBlank() || !imageUrl.isNullOrBlank()
}

enum class PlaybackArtworkSource {
	AurralArtist,
	NativeCover,
	MusicBrainz,
	None
}

@Immutable
data class PlaybackArtistPhotoCacheEntry(
	val cacheKey: String,
	val artistId: String?,
	val sourceArtistId: String?,
	val name: String,
	val normalizedName: String,
	val imageUrl: String,
	val source: String = "Aurral",
	val updatedAtMillis: Long = 0L
)

fun activeArtworkUrl(
	serverArtworkUrl: String?,
	externalArtworkUrl: String?
): String? = serverArtworkUrl.nonBlankOrNull() ?: externalArtworkUrl.nonBlankOrNull()

fun dominantColorArtworkUrl(
	serverArtworkUrl: String?,
	externalArtworkUrl: String?
): String? =
	serverArtworkUrl.nonBlankOrNull()?.withQueryParameter("size", "128")
		?: externalArtworkUrl.nonBlankOrNull()

fun externalFallbackArtworkUrl(
	serverCoverArtId: String?,
	externalArtworkUrl: String?,
	serverCoverLoadFailed: Boolean = false
): String? =
	if (serverCoverArtId.isNullOrBlank() || serverCoverLoadFailed) {
		externalArtworkUrl.nonBlankOrNull()
	} else {
		null
	}

fun externalFallbackArtworkCacheKey(
	serverCoverArtId: String?,
	externalArtworkCacheKey: String?,
	serverCoverLoadFailed: Boolean = false
): String? =
	if (serverCoverArtId.isNullOrBlank() || serverCoverLoadFailed) {
		externalArtworkCacheKey.nonBlankOrNull()
	} else {
		null
	}

fun resolvedPlaybackArtwork(
	serverCoverArtId: String?,
	aurralArtistImageUrl: String?,
	aurralArtistCacheKey: String?,
	musicBrainzArtworkUrl: String?,
	musicBrainzArtworkCacheKey: String?,
	artworkSourcePriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	aurralArtworkEnabled: Boolean = true,
	musicBrainzArtworkEnabled: Boolean = true,
	serverCoverLoadFailed: Boolean = false
): PlaybackArtworkResolution {
	val effectivePriority = effectiveArtworkSourcePriority(
		artworkSourcePriority = artworkSourcePriority,
		aurralArtworkEnabled = aurralArtworkEnabled
	)
	val nativeCover = serverCoverArtId.nonBlankOrNull()
		?.takeUnless { serverCoverLoadFailed }
	val aurralImage = aurralArtistImageUrl.nonBlankOrNull()
		?.takeIf { aurralArtworkEnabled }
	val musicBrainzImage = musicBrainzArtworkUrl.nonBlankOrNull()
		?.takeIf { musicBrainzArtworkEnabled }

	fun aurralResolution(): PlaybackArtworkResolution? =
		aurralImage?.let { image ->
			PlaybackArtworkResolution(
				coverArtId = null,
				imageUrl = image,
				imageCacheKey = externalImageCacheKey(
					prefix = "aurral-artist",
					cacheKey = aurralArtistCacheKey,
					imageUrl = image
				),
				source = PlaybackArtworkSource.AurralArtist
			)
		}

	fun nativeResolution(): PlaybackArtworkResolution? =
		nativeCover?.let { coverArtId ->
			PlaybackArtworkResolution(
				coverArtId = coverArtId,
				imageUrl = null,
				imageCacheKey = null,
				source = PlaybackArtworkSource.NativeCover
			)
		}

	fun musicBrainzResolution(): PlaybackArtworkResolution? =
		musicBrainzImage?.let { image ->
			PlaybackArtworkResolution(
				coverArtId = null,
				imageUrl = image,
				imageCacheKey = externalImageCacheKey(
					prefix = "musicbrainz",
					cacheKey = musicBrainzArtworkCacheKey,
					imageUrl = image
				),
				source = PlaybackArtworkSource.MusicBrainz
			)
		}

	return when (effectivePriority) {
		ArtworkSourcePriority.AurralFirst ->
			aurralResolution()
				?: nativeResolution()
				?: musicBrainzResolution()

		ArtworkSourcePriority.NativeFirst ->
			nativeResolution()
				?: aurralResolution()
				?: musicBrainzResolution()

		ArtworkSourcePriority.NativeOnly ->
			nativeResolution()
	} ?: PlaybackArtworkResolution(
		coverArtId = null,
		imageUrl = null,
		imageCacheKey = null,
		source = PlaybackArtworkSource.None
	)
}

fun effectiveArtworkSourcePriority(
	artworkSourcePriority: ArtworkSourcePriority,
	aurralArtworkEnabled: Boolean
): ArtworkSourcePriority =
	if (aurralArtworkEnabled) {
		ArtworkSourcePriority.AurralFirst
	} else {
		artworkSourcePriority
	}

fun resolvedPlaybackArtistPhoto(
	artistId: String?,
	artistName: String?,
	entries: List<PlaybackArtistPhotoCacheEntry>
): PlaybackArtistPhotoCacheEntry? =
	entries
		.asSequence()
		.filter { entry -> entry.imageUrl.isAbsoluteHttpUrl() }
		.mapNotNull { entry ->
			entry.matchScore(artistId = artistId, artistName = artistName)
				?.let { score -> score to entry }
		}
		.sortedWith(
			compareBy<Pair<PlaybackArtistPhotoMatchScore, PlaybackArtistPhotoCacheEntry>> { it.first.matchRank }
				.thenBy { it.first.sourceRank }
				.thenByDescending { it.second.updatedAtMillis }
		)
		.firstOrNull()
		?.second

fun shouldSendServerArtworkHeaders(
	serverArtworkUrl: String?,
	externalArtworkUrl: String?
): Boolean =
	serverArtworkUrl.nonBlankOrNull() != null || externalArtworkUrl.isNullOrBlank()

private data class PlaybackArtistPhotoMatchScore(
	val matchRank: Int,
	val sourceRank: Int
)

private fun PlaybackArtistPhotoCacheEntry.matchScore(
	artistId: String?,
	artistName: String?
): PlaybackArtistPhotoMatchScore? {
	val normalizedArtistId = artistId.normalizedPlaybackArtworkId()
	val normalizedArtistName = artistName.normalizedPlaybackArtworkName()
	val matchRank = when {
		this.artistId.normalizedPlaybackArtworkId()?.let { it == normalizedArtistId } == true -> 0
		sourceArtistId.normalizedPlaybackArtworkId()?.let { it == normalizedArtistId } == true -> 1
		normalizedName.normalizedPlaybackArtworkName()?.let { it == normalizedArtistName } == true -> 2
		name.normalizedPlaybackArtworkName()?.let { it == normalizedArtistName } == true -> 3
		else -> null
	} ?: return null
	return PlaybackArtistPhotoMatchScore(
		matchRank = matchRank,
		sourceRank = source.playbackArtistPhotoSourceRank()
	)
}

private fun externalImageCacheKey(
	prefix: String,
	cacheKey: String?,
	imageUrl: String
): String? {
	val rawKey = cacheKey.nonBlankOrNull() ?: imageUrl.nonBlankOrNull()
	return rawKey?.let { key ->
		if (key.startsWith("$prefix:", ignoreCase = true)) key else "$prefix:$key"
	}
}

private fun String?.nonBlankOrNull(): String? =
	this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.normalizedPlaybackArtworkId(): String? =
	this
		?.trim()
		?.lowercase()
		?.takeIf { it.isNotEmpty() }

private fun String?.normalizedPlaybackArtworkName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

private fun String.playbackArtistPhotoSourceRank(): Int =
	when (trim().lowercase()) {
		"aurral" -> 0
		"lastfm", "last.fm" -> 1
		else -> 2
	}

private fun String.isAbsoluteHttpUrl(): Boolean =
	startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

private fun String.withQueryParameter(key: String, value: String): String {
	val separator = when {
		contains("?") -> if (endsWith("?") || endsWith("&")) "" else "&"
		else -> "?"
	}
	return "$this$separator$key=$value"
}
