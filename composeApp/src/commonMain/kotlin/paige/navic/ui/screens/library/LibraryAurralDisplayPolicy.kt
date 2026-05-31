package paige.navic.ui.screens.library

import paige.navic.domain.models.AurralAlbumRequest

fun libraryAlbumAurralRequests(
	showAurralHub: Boolean,
	requests: List<AurralAlbumRequest>
): List<AurralAlbumRequest> =
	if (showAurralHub) requests else emptyList()
