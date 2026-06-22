package paige.navic.ui.screens.artist

import androidx.compose.runtime.Immutable
import paige.navic.data.database.entities.ArtistPhotoCacheEntity
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.PlaybackOrigin
import paige.navic.domain.models.settings.ArtworkSourcePriority
import paige.navic.domain.models.toPlaybackOrigin
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.ui.screens.artist.viewmodels.ArtistState

private const val TopSongRowHeightDp = 84
private const val MaxTopSongRows = 3
private const val ArtistBiographyPreviewLimit = 200

fun artistTopSongsGridRows(songCount: Int): Int =
	songCount.coerceIn(0, MaxTopSongRows)

fun artistTopSongsGridHeightDp(songCount: Int): Int =
	artistTopSongsGridRows(songCount) * TopSongRowHeightDp

fun artistDetailHeadingImageUrl(
	artist: DomainArtist,
	verifiedExternalImageUrl: String? = null,
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): String? {
	val verifiedImageUrl = verifiedExternalImageUrl?.trim()?.takeIf { it.isNotEmpty() }
	if (!externalArtworkEnabled || verifiedImageUrl == null) return null
	return verifiedImageUrl
}

fun artistDetailHeadingCoverArtId(
	artist: DomainArtist,
	externalArtworkEnabled: Boolean = true
): String? =
	artistCoverArtIdForExternalArtworkPolicy(
		artist = artist,
		externalArtworkEnabled = externalArtworkEnabled
	)

fun artistCoverArtIdForExternalArtworkPolicy(
	artist: DomainArtist,
	externalArtworkEnabled: Boolean = true
): String? =
	if (externalArtworkEnabled) {
		null
	} else {
		artist.coverArtId?.trim()?.takeIf { it.isNotEmpty() }
	}

fun artistImageUrlForExternalArtworkPolicy(
	artist: DomainArtist,
	externalArtworkEnabled: Boolean = true
): String? {
	if (!externalArtworkEnabled) return null
	val imageUrl = artist.artistImageUrl
		?.trim()
		?.takeIf { it.isNotEmpty() && it.isAbsoluteHttpUrl() }
		?: return null
	return imageUrl.takeUnless { it.isNavidromeArtworkUrl() }
}

@Immutable
data class ArtistHeaderImageCacheEntry(
	val artistId: String?,
	val sourceArtistId: String?,
	val name: String,
	val normalizedName: String,
	val imageUrl: String,
	val source: String = "Aurral",
	val updatedAtMillis: Long = 0L
)

@Immutable
data class ArtistListAurralPhotoHydrationTarget(
	val artist: DomainArtist,
	val lookupKey: String
)

fun artistDetailCachedImageUrl(
	artist: DomainArtist,
	entries: List<ArtistHeaderImageCacheEntry>,
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): String? {
	if (!externalArtworkEnabled) return null
	return artistDetailCachedImageEntry(artist, entries)?.imageUrl?.trim()
}

fun ArtistPhotoCacheEntity.toArtistHeaderImageCacheEntry(): ArtistHeaderImageCacheEntry =
	ArtistHeaderImageCacheEntry(
		artistId = artistId,
		sourceArtistId = sourceArtistId,
		name = name,
		normalizedName = normalizedName,
		imageUrl = imageUrl,
		source = source,
		updatedAtMillis = updatedAtMillis
	)

fun DomainArtist.withCachedArtistPhoto(
	entries: List<ArtistHeaderImageCacheEntry>,
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): DomainArtist =
	copy(
		artistImageUrl = artistDetailCachedImageUrl(
			artist = this,
			entries = entries,
			artistArtworkPriority = artistArtworkPriority,
			externalArtworkEnabled = externalArtworkEnabled
		)
	)

fun artistListAurralPhotoHydrationTargets(
	artists: List<DomainArtist>,
	attemptedLookupKeys: Set<String>,
	externalArtworkEnabled: Boolean,
	limit: Int = Int.MAX_VALUE
): List<ArtistListAurralPhotoHydrationTarget> {
	if (!externalArtworkEnabled || limit <= 0) return emptyList()
	return artists
		.asSequence()
		.mapNotNull { artist ->
			val lookupKey = artistListAurralPhotoLookupKey(artist) ?: return@mapNotNull null
			if (lookupKey in attemptedLookupKeys) return@mapNotNull null
			if (artist.artistImageUrl.isResolvedExternalArtistImageUrl()) return@mapNotNull null
			ArtistListAurralPhotoHydrationTarget(artist = artist, lookupKey = lookupKey)
		}
		.distinctBy { target -> target.lookupKey }
		.take(limit)
		.toList()
}

fun artistListAurralPhotoCandidate(
	localArtist: DomainArtist,
	candidates: List<AurralDiscoverArtist>
): AurralDiscoverArtist? {
	val localMusicBrainzId = localArtist.musicBrainzId.normalizedArtistHeaderImageId()
	val localName = localArtist.name.normalizedArtistHeaderImageName()
	val withImages = candidates.filter { candidate -> candidate.imageUrl?.isAbsoluteHttpUrl() == true }
	return withImages.firstOrNull { candidate ->
		candidate.id.normalizedArtistHeaderImageId()?.let { it == localMusicBrainzId } == true
	} ?: withImages.firstOrNull { candidate ->
		candidate.name.normalizedArtistHeaderImageName()?.let { it == localName } == true
	} ?: withImages.firstOrNull()
}

fun artistListAurralPhotoCacheEntity(
	localArtist: DomainArtist,
	sourceArtist: AurralDiscoverArtist?,
	nowMillis: Long
): ArtistPhotoCacheEntity? {
	val candidate = sourceArtist ?: return null
	return artistDetailPhotoCacheEntity(
		localArtist = localArtist,
		sourceArtist = DomainArtist(
			id = candidate.id,
			name = candidate.name,
			musicBrainzId = candidate.id.takeIf { candidate.detailsIdVerified }
		),
		imageUrl = candidate.imageUrl.orEmpty(),
		nowMillis = nowMillis,
		source = "Aurral"
	)
}

fun artistListAurralPhotoLookupKey(artist: DomainArtist): String? {
	val id = artist.id.trim().lowercase().takeIf { it.isNotEmpty() }
	val name = artist.name.normalizedArtistHeaderImageName()
	return when {
		id != null && name != null -> "$id|$name"
		name != null -> "name:$name"
		id != null -> "id:$id"
		else -> null
	}
}

fun artistDetailPhotoCacheEntity(
	localArtist: DomainArtist,
	sourceArtist: DomainArtist,
	imageUrl: String,
	nowMillis: Long,
	source: String = "Aurral"
): ArtistPhotoCacheEntity? {
	val resolvedImageUrl = imageUrl.trim().takeIf { it.isAbsoluteHttpUrl() } ?: return null
	val normalizedName = listOf(localArtist.name, sourceArtist.name)
		.firstNotNullOfOrNull { it.normalizedArtistHeaderImageName() }
		?: return null
	val artistId = localArtist.id.trim().takeIf { it.isNotEmpty() }
	val sourceArtistId = sourceArtist.musicBrainzId
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: sourceArtist.id
			.trim()
			.takeIf { it.isNotEmpty() && it != artistId }
	val cacheKey = artistId?.let { "artist:$it" }
		?: sourceArtistId?.let { "source:$it" }
		?: "name:$normalizedName"
	return ArtistPhotoCacheEntity(
		cacheKey = cacheKey,
		artistId = artistId,
		sourceArtistId = sourceArtistId,
		name = sourceArtist.name.trim().takeIf { it.isNotEmpty() } ?: localArtist.name,
		normalizedName = normalizedName,
		imageUrl = resolvedImageUrl,
		source = source,
		updatedAtMillis = nowMillis
	)
}

fun artistDetailPlaybackOrigin(
	state: ArtistState,
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): PlaybackOrigin {
	val resolvedArtwork = artistDetailHeadingImageUrl(
		artist = state.artist,
		verifiedExternalImageUrl = state.aurralArtistImageUrl,
		artistArtworkPriority = artistArtworkPriority,
		externalArtworkEnabled = externalArtworkEnabled
	)
	return state.artist.toPlaybackOrigin().copy(
		coverArtId = resolvedArtwork
			?: artistDetailHeadingCoverArtId(
				artist = state.artist,
				externalArtworkEnabled = externalArtworkEnabled
			)
	)
}

@Immutable
data class ArtistDetailTransitionKey(
	val artistId: String
)

fun artistDetailTransitionKey(state: ArtistState): ArtistDetailTransitionKey =
	ArtistDetailTransitionKey(artistId = state.artist.id)

fun shouldAnimateArtistDetailStateChange(
	initial: ArtistState,
	target: ArtistState
): Boolean =
	artistDetailTransitionKey(initial) != artistDetailTransitionKey(target)

fun artistBiographyDisplayText(
	biography: String?,
	expanded: Boolean,
	limit: Int = ArtistBiographyPreviewLimit
): String? {
	val text = biography?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	return if (!expanded && text.length > limit) {
		text.take(limit) + "..."
	} else {
		text
	}
}

fun shouldShowArtistBiographyToggle(
	biography: String?,
	limit: Int = ArtistBiographyPreviewLimit
): Boolean =
	biography?.trim()?.let { it.length > limit } == true

@Immutable
data class ArtistBiographyScrollFades(
	val showTop: Boolean,
	val showBottom: Boolean
)

fun artistBiographyScrollFades(
	scrollValue: Int,
	maxScrollValue: Int
): ArtistBiographyScrollFades =
	ArtistBiographyScrollFades(
		showTop = scrollValue > 0,
		showBottom = maxScrollValue > 0 && scrollValue < maxScrollValue
	)

private fun artistDetailCachedImageEntry(
	artist: DomainArtist,
	entries: List<ArtistHeaderImageCacheEntry>
): ArtistHeaderImageCacheEntry? =
	entries
		.asSequence()
		.filter { entry -> entry.imageUrl.isResolvedExternalArtistImageUrl() }
		.mapNotNull { entry -> entry.matchScore(artist)?.let { score -> score to entry } }
		.sortedWith(
			compareBy<Pair<ArtistHeaderImageCacheMatchScore, ArtistHeaderImageCacheEntry>> { it.first.matchRank }
				.thenBy { it.first.sourceRank }
				.thenByDescending { it.second.updatedAtMillis }
		)
		.firstOrNull()
		?.second

private data class ArtistHeaderImageCacheMatchScore(
	val matchRank: Int,
	val sourceRank: Int
)

private fun ArtistHeaderImageCacheEntry.matchScore(artist: DomainArtist): ArtistHeaderImageCacheMatchScore? {
	val localArtistId = artist.id.normalizedArtistHeaderImageId()
	val musicBrainzId = artist.musicBrainzId.normalizedArtistHeaderImageId()
	val artistName = artist.name.normalizedArtistHeaderImageName()
	val matchRank = when {
		artistId.normalizedArtistHeaderImageId()?.let { it == localArtistId } == true -> 0
		artistId.normalizedArtistHeaderImageId()?.let { it == musicBrainzId } == true -> 1
		sourceArtistId.normalizedArtistHeaderImageId()?.let { id ->
			id == localArtistId || id == musicBrainzId
		} == true -> 2
		normalizedName.normalizedArtistHeaderImageName()?.let { it == artistName } == true -> 3
		name.normalizedArtistHeaderImageName()?.let { it == artistName } == true -> 4
		else -> null
	} ?: return null
	return ArtistHeaderImageCacheMatchScore(
		matchRank = matchRank,
		sourceRank = source.artistHeaderImageSourceRank()
	)
}

private fun String?.normalizedArtistHeaderImageId(): String? =
	this
		?.trim()
		?.lowercase()
		?.takeIf { it.isNotEmpty() }

private fun String?.normalizedArtistHeaderImageName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

private fun String.isAbsoluteHttpUrl(): Boolean =
	startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

private fun String?.isResolvedExternalArtistImageUrl(): Boolean {
	val imageUrl = this?.trim()?.takeIf { it.isNotEmpty() } ?: return false
	return imageUrl.isAbsoluteHttpUrl() && !imageUrl.isNavidromeArtworkUrl()
}

private fun String.isNavidromeArtworkUrl(): Boolean {
	val normalized = lowercase()
	return "navidrome" in normalized ||
		"/rest/getcoverart" in normalized ||
		"/rest/getartistimage" in normalized ||
		"/getcoverart" in normalized ||
		"/getartistimage" in normalized
}

private fun String.artistHeaderImageSourceRank(): Int =
	when (trim().lowercase()) {
		"aurral" -> 0
		"lastfm", "last.fm" -> 1
		else -> 2
	}
