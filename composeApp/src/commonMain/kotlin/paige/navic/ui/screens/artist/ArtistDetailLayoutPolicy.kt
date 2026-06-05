package paige.navic.ui.screens.artist

import androidx.compose.runtime.Immutable
import paige.navic.data.database.entities.ArtistPhotoCacheEntity
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.PlaybackOrigin
import paige.navic.domain.models.settings.ArtworkSourcePriority
import paige.navic.domain.models.toPlaybackOrigin
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
	return when (artistArtworkPriority) {
		ArtworkSourcePriority.AurralFirst -> verifiedImageUrl
		ArtworkSourcePriority.NativeFirst ->
			verifiedImageUrl.takeIf { artist.coverArtId.isNullOrBlank() }

		ArtworkSourcePriority.NativeOnly -> null
	}
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

fun artistDetailCachedImageUrl(
	artist: DomainArtist,
	entries: List<ArtistHeaderImageCacheEntry>,
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): String? {
	if (!externalArtworkEnabled || artistArtworkPriority == ArtworkSourcePriority.NativeOnly) return null
	if (artistArtworkPriority == ArtworkSourcePriority.NativeFirst && !artist.coverArtId.isNullOrBlank()) return null
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
		coverArtId = resolvedArtwork ?: state.artist.coverArtId?.trim()?.takeIf { it.isNotEmpty() }
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
		.filter { entry -> entry.imageUrl.isAbsoluteHttpUrl() }
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

private fun String.artistHeaderImageSourceRank(): Int =
	when (trim().lowercase()) {
		"aurral" -> 0
		"lastfm", "last.fm" -> 1
		else -> 2
	}
