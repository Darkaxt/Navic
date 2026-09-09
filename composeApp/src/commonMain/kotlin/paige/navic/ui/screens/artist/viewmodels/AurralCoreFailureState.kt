package paige.navic.ui.screens.artist.viewmodels

// These sections have not started: all require the failed core identity lookup.
internal fun ArtistState.withAurralCoreFailure(message: String): ArtistState = copy(
	aurralLoading = false,
	aurralProfileLoading = false,
	aurralOwnershipLoading = false,
	aurralPreviewTracksLoading = false,
	aurralSimilarArtistsLoading = false,
	aurralRequestsLoading = false,
	aurralError = message,
	aurralProfileError = message,
	aurralOwnershipError = message,
	aurralPreviewTracksError = message,
	aurralSimilarArtistsError = message,
	aurralRequestsError = message
)
