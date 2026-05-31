package paige.navic.ui.screens.library

import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary
import paige.navic.ui.screens.aurral.aurralHubDiscoverArtists

fun libraryAlbumAurralRequests(
	showAurralHub: Boolean,
	requests: List<AurralAlbumRequest>
): List<AurralAlbumRequest> =
	if (showAurralHub) requests else emptyList()

fun libraryAurralDiscoverArtists(
	aurralConfigured: Boolean,
	discovery: AurralDiscoverySummary?,
	limit: Int = 8
): List<AurralDiscoverArtist> =
	if (aurralConfigured && discovery != null) {
		aurralHubDiscoverArtists(discovery, limit)
	} else {
		emptyList()
	}
