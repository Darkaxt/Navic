package paige.navic.domain.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class IntegrationService {
	LidaClips,
	Aurral,
	MusicBrainz,
	LastFm,
	Bindery
}

private val integrationAttemptStatusJson = Json {
	ignoreUnknownKeys = true
	isLenient = true
}

@Serializable
private data class IntegrationAttemptStatusStore(
	val failedServices: List<String> = emptyList()
)

fun integrationAttemptFailedServices(json: String): Set<IntegrationService> {
	if (json.isBlank()) return emptySet()
	return runCatching {
		integrationAttemptStatusJson
			.decodeFromString<IntegrationAttemptStatusStore>(json)
			.failedServices
			.mapNotNull { value -> value.toIntegrationServiceOrNull() }
			.toSet()
	}.getOrDefault(emptySet())
}

fun markIntegrationServiceDown(
	json: String,
	service: IntegrationService
): String =
	encodeIntegrationAttemptFailedServices(
		integrationAttemptFailedServices(json) + service
	)

fun markIntegrationServiceAvailable(
	json: String,
	service: IntegrationService
): String =
	encodeIntegrationAttemptFailedServices(
		integrationAttemptFailedServices(json) - service
	)

fun visibleFailedIntegrationServices(
	failedServices: Set<IntegrationService>,
	enabledServices: Set<IntegrationService>,
	loadingServices: Set<IntegrationService>,
	relevantServices: Set<IntegrationService> = enabledServices
): List<IntegrationService> =
	IntegrationService.entries.filter { service ->
		service in failedServices &&
			service in enabledServices &&
			service in relevantServices &&
			service !in loadingServices
	}

private fun encodeIntegrationAttemptFailedServices(
	services: Set<IntegrationService>
): String {
	val normalizedServices = IntegrationService.entries
		.filter(services::contains)
		.map(IntegrationService::name)
	if (normalizedServices.isEmpty()) return ""
	return integrationAttemptStatusJson.encodeToString(
		IntegrationAttemptStatusStore(failedServices = normalizedServices)
	)
}

private fun String.toIntegrationServiceOrNull(): IntegrationService? =
	IntegrationService.entries.firstOrNull { service -> service.name == this }
