package paige.navic.ui.screens.settings

import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.isSyntheticUnknownArtistName
import paige.navic.domain.repositories.LidaClipsHealthCheck
import paige.navic.domain.repositories.LidaClipsRecentFailure

internal data class LidaClipsHealthFailureDisplay(
	val name: String,
	val detail: String?
)

internal fun lidaClipsRecentClipTitle(clip: DomainLidaClip): String =
	clip.track.cleanLidaClipsDisplayText()
		?: clip.title.cleanLidaClipsDisplayText()
		?: clip.fileName.cleanLidaClipsDisplayText()
		?: "Music video"

internal fun lidaClipsRecentClipSubtitle(clip: DomainLidaClip): String? =
	listOfNotNull(
		clip.artist.cleanLidaClipsDisplayText(skipSyntheticUnknownArtist = true),
		clip.album.cleanLidaClipsDisplayText(),
		clip.qualityTier.cleanLidaClipsDisplayText()
	).joinToString(" - ").takeIf { it.isNotEmpty() }

internal fun lidaClipsRecentFailureTitle(
	failure: LidaClipsRecentFailure,
	unknownTrackText: String,
	lidarrTrackText: String?
): String {
	val artist = failure.artist.cleanLidaClipsDisplayText(skipSyntheticUnknownArtist = true)
	val track = failure.track.cleanLidaClipsDisplayText()
	val album = failure.album.cleanLidaClipsDisplayText()
	return when {
		artist != null && track != null -> "$artist - $track"
		track != null -> track
		artist != null && album != null -> "$artist - $album"
		artist != null -> artist
		album != null -> album
		failure.lidarrTrackId != null -> lidarrTrackText ?: unknownTrackText
		else -> unknownTrackText
	}
}

internal fun lidaClipsHealthFailureDisplay(check: LidaClipsHealthCheck): LidaClipsHealthFailureDisplay =
	LidaClipsHealthFailureDisplay(
		name = check.name.replace('_', ' ').cleanLidaClipsDisplayText() ?: "unknown",
		detail = check.error.cleanLidaClipsDisplayText()
			?: check.address.cleanLidaClipsDisplayText()
			?: check.path.cleanLidaClipsDisplayText()
	)

internal fun String?.cleanLidaClipsDisplayText(
	skipSyntheticUnknownArtist: Boolean = false
): String? =
	this
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?.takeUnless { skipSyntheticUnknownArtist && isSyntheticUnknownArtistName(it) }
