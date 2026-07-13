package paige.navic.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.IntegrationService
import paige.navic.data.remote.aurral.configuredAurralBaseUrl
import paige.navic.data.remote.bindery.configuredBinderyOpdsBaseUrl
import paige.navic.domain.repositories.configuredLidaClipsBaseUrl

val MusicIntegrationServices = setOf(
	IntegrationService.LidaClips,
	IntegrationService.Aurral,
	IntegrationService.MusicBrainz,
	IntegrationService.LastFm
)

val AurralIntegrationServices = setOf(IntegrationService.Aurral)

val LidaClipsIntegrationServices = setOf(IntegrationService.LidaClips)

val LastFmIntegrationServices = setOf(IntegrationService.LastFm)

val MusicBrainzIntegrationServices = setOf(IntegrationService.MusicBrainz)

val BinderyIntegrationServices = setOf(IntegrationService.Bindery)

val ActivityIntegrationServices = setOf(
	IntegrationService.Aurral,
	IntegrationService.LidaClips
)

fun enabledIntegrationServices(preferenceManager: PreferenceManager): Set<IntegrationService> =
	buildSet {
		if (
			preferenceManager.lidaClipsEnabled &&
			configuredLidaClipsBaseUrl(preferenceManager.lidaClipsBaseUrl) != null
		) {
			add(IntegrationService.LidaClips)
		}
		if (
			preferenceManager.aurralEnabled &&
			configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) != null
		) {
			add(IntegrationService.Aurral)
		}
		if (preferenceManager.musicBrainzArtworkFallbackEnabled) {
			add(IntegrationService.MusicBrainz)
		}
		if (preferenceManager.lastFmEnabled && preferenceManager.lastFmApiKey.isNotBlank()) {
			add(IntegrationService.LastFm)
		}
		if (
			preferenceManager.binderyEnabled &&
			configuredBinderyOpdsBaseUrl(preferenceManager.binderyOpdsBaseUrl) != null &&
			preferenceManager.binderyApiKey.isNotBlank()
		) {
			add(IntegrationService.Bindery)
		}
	}

fun integrationFailedIndicators(
	preferenceManager: PreferenceManager,
	loadingIndicators: List<IntegrationLoadingIndicator>,
	relevantServices: Set<IntegrationService> = enabledIntegrationServices(preferenceManager)
): List<IntegrationLoadingIndicator> =
	integrationFailedIndicators(
		failedServices = preferenceManager.failedIntegrationServices,
		enabledServices = enabledIntegrationServices(preferenceManager),
		loadingIndicators = loadingIndicators,
		relevantServices = relevantServices
	)

@Composable
fun TrackIntegrationServiceAttemptStatus(
	preferenceManager: PreferenceManager,
	service: IntegrationService,
	enabled: Boolean,
	loading: Boolean,
	failed: Boolean,
	available: Boolean
) {
	LaunchedEffect(service, enabled, loading, failed, available) {
		if (!enabled || loading) return@LaunchedEffect
		when {
			failed -> preferenceManager.markIntegrationServiceDown(service)
			available -> preferenceManager.markIntegrationServiceAvailable(service)
		}
	}
}
