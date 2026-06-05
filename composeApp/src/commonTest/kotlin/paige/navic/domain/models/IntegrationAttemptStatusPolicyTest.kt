package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class IntegrationAttemptStatusPolicyTest {
	@Test
	fun failedServicesPersistUntilMarkedAvailable() {
		val failedJson = markIntegrationServiceDown(
			json = "",
			service = IntegrationService.LidaClips
		)

		assertEquals(setOf(IntegrationService.LidaClips), integrationAttemptFailedServices(failedJson))

		val recoveredJson = markIntegrationServiceAvailable(
			json = failedJson,
			service = IntegrationService.LidaClips
		)

		assertEquals(emptySet(), integrationAttemptFailedServices(recoveredJson))
	}

	@Test
	fun visibleFailuresRequireAnEnabledService() {
		val failedJson = markIntegrationServiceDown("", IntegrationService.Aurral)
		val failedServices = integrationAttemptFailedServices(failedJson)

		assertEquals(
			emptyList(),
			visibleFailedIntegrationServices(
				failedServices = failedServices,
				enabledServices = emptySet(),
				loadingServices = emptySet()
			)
		)
		assertEquals(
			listOf(IntegrationService.Aurral),
			visibleFailedIntegrationServices(
				failedServices = failedServices,
				enabledServices = setOf(IntegrationService.Aurral),
				loadingServices = emptySet()
			)
		)
	}

	@Test
	fun loadingServiceSuppressesTheFrozenFailedBadge() {
		val failedJson = markIntegrationServiceDown("", IntegrationService.MusicBrainz)

		assertEquals(
			emptyList(),
			visibleFailedIntegrationServices(
				failedServices = integrationAttemptFailedServices(failedJson),
				enabledServices = setOf(IntegrationService.MusicBrainz),
				loadingServices = setOf(IntegrationService.MusicBrainz)
			)
		)
	}

	@Test
	fun visibleFailuresAreScopedToRelevantPageServices() {
		val failedJson = markIntegrationServiceDown("", IntegrationService.Bindery)
		val failedServices = integrationAttemptFailedServices(failedJson)

		assertEquals(
			emptyList(),
			visibleFailedIntegrationServices(
				failedServices = failedServices,
				enabledServices = setOf(IntegrationService.Bindery),
				loadingServices = emptySet(),
				relevantServices = setOf(IntegrationService.Aurral, IntegrationService.MusicBrainz)
			)
		)
		assertEquals(
			listOf(IntegrationService.Bindery),
			visibleFailedIntegrationServices(
				failedServices = failedServices,
				enabledServices = setOf(IntegrationService.Bindery),
				loadingServices = emptySet(),
				relevantServices = setOf(IntegrationService.Bindery)
			)
		)
	}

	@Test
	fun corruptPersistedStatusDecodesAsEmpty() {
		assertEquals(emptySet(), integrationAttemptFailedServices("not json"))
	}
}
