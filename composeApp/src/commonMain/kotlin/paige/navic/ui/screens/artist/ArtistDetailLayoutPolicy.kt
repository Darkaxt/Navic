package paige.navic.ui.screens.artist

import androidx.compose.runtime.Immutable
import paige.navic.domain.models.DomainArtist
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
