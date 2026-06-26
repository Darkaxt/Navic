package paige.navic.ui.screens.artist

import androidx.compose.runtime.Immutable
import paige.navic.ui.screens.artist.viewmodels.ArtistState

@Immutable
data class AurralArtistProfileUiState(
	val enabled: Boolean,
	val candidateArtistMbid: String?,
	val displayName: String,
	val displayBio: String?,
	val profile: AurralArtistSectionUiState,
	val monitor: AurralArtistMonitorUiState,
	val ownership: AurralArtistSectionUiState,
	val previewTracks: AurralArtistSectionUiState,
	val similarArtists: AurralArtistSectionUiState,
	val requests: AurralArtistSectionUiState,
	val localPlayback: AurralArtistSectionUiState,
	val monitorActionVisible: Boolean,
	val monitorActionEnabled: Boolean
)

@Immutable
enum class AurralArtistSectionUiState {
	Disabled,
	Loading,
	Ready,
	Empty,
	Error
}

@Immutable
enum class AurralArtistMonitorUiState {
	UnknownResolving,
	VerifiedMonitored,
	VerifiedUnmonitored,
	Updating,
	Error
}

fun aurralArtistProfileUiState(
	state: ArtistState,
	aurralEnabled: Boolean,
	monitoringInAurral: Boolean,
	monitorPendingInAurral: Boolean
): AurralArtistProfileUiState {
	val candidateArtistMbid = state.aurralArtistMbid
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: state.artist.musicBrainzId?.trim()?.takeIf { it.isNotEmpty() }
	val aurralHasProfile = !state.aurralArtistName.isNullOrBlank() ||
		!state.aurralArtistBio.isNullOrBlank() ||
		state.aurralArtistGenres.isNotEmpty() ||
		state.aurralArtistExternalLinks.isNotEmpty() ||
		!state.aurralArtistImageUrl.isNullOrBlank()
	val aurralHasOwnership = state.aurralOwnedOrPartialAlbums.isNotEmpty() ||
		state.aurralMissingReleaseGroups.isNotEmpty()
	val aurralHasRequests = state.aurralAlbumRequests.isNotEmpty() ||
		(state.aurralOwnedOrPartialAlbums + state.aurralMissingReleaseGroups).any { it.requestStatus != null }
	val monitorVisible = aurralEnabled && !candidateArtistMbid.isNullOrBlank()
	val monitor = when {
		!monitorVisible -> AurralArtistMonitorUiState.Error
		monitoringInAurral || monitorPendingInAurral -> AurralArtistMonitorUiState.Updating
		state.aurralMonitored == true -> AurralArtistMonitorUiState.VerifiedMonitored
		state.aurralMonitored == false -> AurralArtistMonitorUiState.VerifiedUnmonitored
		state.aurralError != null -> AurralArtistMonitorUiState.Error
		else -> AurralArtistMonitorUiState.UnknownResolving
	}
	return AurralArtistProfileUiState(
		enabled = aurralEnabled,
		candidateArtistMbid = candidateArtistMbid,
		displayName = state.aurralArtistName?.trim()?.takeIf { it.isNotEmpty() } ?: state.artist.name,
		displayBio = state.aurralArtistBio?.trim()?.takeIf { it.isNotEmpty() } ?: state.artist.biography,
		profile = aurralSectionState(
			enabled = aurralEnabled,
			loading = state.aurralLoading,
			error = state.aurralError != null && !aurralHasProfile,
			ready = aurralHasProfile
		),
		monitor = monitor,
		ownership = aurralSectionState(
			enabled = aurralEnabled,
			loading = state.aurralLoading,
			error = state.aurralError != null && !aurralHasOwnership,
			ready = aurralHasOwnership
		),
		previewTracks = aurralSectionState(
			enabled = aurralEnabled,
			loading = state.aurralLoading,
			error = false,
			ready = state.aurralPreviewTracks.isNotEmpty()
		),
		similarArtists = aurralSectionState(
			enabled = aurralEnabled,
			loading = state.aurralLoading,
			error = false,
			ready = state.aurralSimilarArtists.isNotEmpty()
		),
		requests = aurralSectionState(
			enabled = aurralEnabled,
			loading = state.aurralLoading,
			error = false,
			ready = aurralHasRequests
		),
		localPlayback = if (state.albums.isNotEmpty() || state.topSongs.isNotEmpty()) {
			AurralArtistSectionUiState.Ready
		} else {
			AurralArtistSectionUiState.Empty
		},
		monitorActionVisible = monitorVisible,
		monitorActionEnabled = monitorVisible && monitor != AurralArtistMonitorUiState.Updating &&
			monitor != AurralArtistMonitorUiState.Error
	)
}

private fun aurralSectionState(
	enabled: Boolean,
	loading: Boolean,
	error: Boolean,
	ready: Boolean
): AurralArtistSectionUiState =
	when {
		!enabled -> AurralArtistSectionUiState.Disabled
		ready -> AurralArtistSectionUiState.Ready
		error -> AurralArtistSectionUiState.Error
		loading -> AurralArtistSectionUiState.Loading
		else -> AurralArtistSectionUiState.Empty
	}
