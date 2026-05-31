package paige.navic.ui.screens.artist

import paige.navic.domain.models.AurralSimilarArtistRow
import paige.navic.ui.navigation.Screen

fun aurralExternalArtistRoute(row: AurralSimilarArtistRow): Screen.AurralArtist? {
	if (row.localArtistId != null) return null
	val artistMbid = row.artist.id.trim().takeIf { it.isNotEmpty() } ?: return null
	val artistName = row.artist.name.trim().takeIf { it.isNotEmpty() } ?: return null
	return Screen.AurralArtist(
		artistMbid = artistMbid,
		artistName = artistName,
		imageUrl = row.artist.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
	)
}
