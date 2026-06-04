package paige.navic.ui.screens.artist

import androidx.compose.runtime.Immutable
import paige.navic.data.database.entities.ArtistPhotoCacheEntity
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.PlaybackOrigin
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
	verifiedExternalImageUrl: String? = null
): String? {
	val verifiedImageUrl = verifiedExternalImageUrl?.trim()?.takeIf { it.isNotEmpty() }
	if (verifiedImageUrl != null) return verifiedImageUrl
	return artist.artistImageUrl?.trim()?.takeIf { it.isNotEmpty() }
}

@Immutable
data class ArtistHeaderImageCacheEntry(
	val artistId: String?,
	val sourceArtistId: String?,
	val name: String,
	val normalizedName: String,
	val imageUrl: String
)

fun artistDetailCachedImageUrl(
	artist: DomainArtist,
	entries: List<ArtistHeaderImageCacheEntry>
): String? =
	entries.firstOrNull { entry ->
		entry.imageUrl.isAbsoluteHttpUrl() && entry.matches(artist)
	}?.imageUrl?.trim()

fun ArtistPhotoCacheEntity.toArtistHeaderImageCacheEntry(): ArtistHeaderImageCacheEntry =
	ArtistHeaderImageCacheEntry(
		artistId = artistId,
		sourceArtistId = sourceArtistId,
		name = name,
		normalizedName = normalizedName,
		imageUrl = imageUrl
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

fun artistDetailPlaybackOrigin(state: ArtistState): PlaybackOrigin {
	val resolvedArtwork = artistDetailHeadingImageUrl(
		artist = state.artist,
		verifiedExternalImageUrl = state.aurralArtistImageUrl
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

private fun ArtistHeaderImageCacheEntry.matches(artist: DomainArtist): Boolean {
	val localArtistId = artist.id.normalizedArtistHeaderImageId()
	val musicBrainzId = artist.musicBrainzId.normalizedArtistHeaderImageId()
	val artistName = artist.name.normalizedArtistHeaderImageName()
	return artistId.normalizedArtistHeaderImageId()?.let { id ->
		id == localArtistId || id == musicBrainzId
	} == true ||
		sourceArtistId.normalizedArtistHeaderImageId()?.let { id ->
			id == localArtistId || id == musicBrainzId
		} == true ||
		normalizedName.normalizedArtistHeaderImageName()?.let { it == artistName } == true ||
		name.normalizedArtistHeaderImageName()?.let { it == artistName } == true
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
