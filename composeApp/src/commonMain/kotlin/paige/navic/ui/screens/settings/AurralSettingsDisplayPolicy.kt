package paige.navic.ui.screens.settings

import paige.navic.domain.repositories.AurralConnectionResult
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.data.remote.aurral.configuredAurralBaseUrl

internal sealed interface AurralConnectionStatusDisplay {
	data object MissingUrl : AurralConnectionStatusDisplay
	data object InvalidUrl : AurralConnectionStatusDisplay
	data object Testing : AurralConnectionStatusDisplay
	data object NotTested : AurralConnectionStatusDisplay
	data object Connected : AurralConnectionStatusDisplay
	data object Unauthorized : AurralConnectionStatusDisplay
	data object Forbidden : AurralConnectionStatusDisplay
	data class Failed(val message: String) : AurralConnectionStatusDisplay
}

internal fun aurralConnectionStatusDisplay(
	baseUrl: String,
	connectionResult: AurralConnectionResult?,
	isTestingConnection: Boolean
): AurralConnectionStatusDisplay {
	if (isTestingConnection) return AurralConnectionStatusDisplay.Testing
	if (baseUrl.isBlank()) return AurralConnectionStatusDisplay.MissingUrl
	if (configuredAurralBaseUrl(baseUrl) == null) return AurralConnectionStatusDisplay.InvalidUrl

	return when (connectionResult) {
		null -> AurralConnectionStatusDisplay.NotTested
		AurralConnectionResult.Connected -> AurralConnectionStatusDisplay.Connected
		AurralConnectionResult.Unauthorized -> AurralConnectionStatusDisplay.Unauthorized
		AurralConnectionResult.Forbidden -> AurralConnectionStatusDisplay.Forbidden
		is AurralConnectionResult.Failed ->
			AurralConnectionStatusDisplay.Failed(connectionResult.message)
	}
}

internal fun aurralPermissionSummary(status: AurralServiceStatus): String {
	if (status.username.isNullOrBlank()) return "Not authenticated"
	val permissions = buildList {
		if (status.accessFlow) add("Flows")
		if (status.addArtist) add("artist requests")
		if (status.addAlbum) add("album requests")
		if (status.changeMonitoring) add("artist monitoring")
	}
	return permissions.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "No native actions"
}

internal fun aurralFlowTrackSummary(status: AurralServiceStatus): String {
	val parts = buildList {
		add("${status.flowTracksTotal} total")
		if (status.flowTracksPending > 0) add("${status.flowTracksPending} pending")
		if (status.flowTracksDownloading > 0) add("${status.flowTracksDownloading} downloading")
		if (status.flowTracksDone > 0) add("${status.flowTracksDone} ready")
		if (status.flowTracksFailed > 0) add("${status.flowTracksFailed} failed")
	}
	return parts.joinToString(", ")
}
