package paige.navic.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.IntegrationService
import paige.navic.domain.repositories.configuredAurralBaseUrl
import paige.navic.domain.repositories.configuredBinderyOpdsBaseUrl
import paige.navic.domain.repositories.configuredLidaClipsBaseUrl

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
	loadingIndicators: List<IntegrationLoadingIndicator>
): List<IntegrationLoadingIndicator> =
	integrationFailedIndicators(
		failedServices = preferenceManager.failedIntegrationServices,
		enabledServices = enabledIntegrationServices(preferenceManager),
		loadingIndicators = loadingIndicators
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
