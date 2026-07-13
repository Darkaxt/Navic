package paige.navic.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class OptionalIntegrationViewModelSourceTest {
	@Test
	fun aurralHubPublishesTypedBaseAndIncrementalAvailability() {
		val source = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralHubViewModel.kt"
		).readText()

		assertContains(
			source,
			"MutableStateFlow<OptionalIntegrationResult<AurralDiscoverySummary>?>(null)"
		)
		assertContains(source, "val discoveryAvailability = _discoveryAvailability.asStateFlow()")
		assertContains(source, "repository.getDiscoveryOptional(")
		assertContains(source, "repository.getDiscoveryRecentlyAddedOptional()")
		assertContains(source, "repository.getDiscoveryRecentReleasesOptional()")
		assertContains(source, "is OptionalIntegrationResult.Stale ->")
	}

	@Test
	fun binderyHubAggregatesTypedRowsWithoutDiscardingFailures() {
		val source = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyHubViewModel.kt"
		).readText()
		val loadHub = source.substringAfter("private suspend fun loadHub(")
			.substringBefore("private suspend fun loadContinueListening(")

		assertContains(
			source,
			"MutableStateFlow<OptionalIntegrationResult<BinderyHubState>?>(null)"
		)
		assertContains(source, "val hubAvailability = _hubAvailability.asStateFlow()")
		assertContains(loadHub, "repository.getCatalogOptional(\"/\")")
		assertContains(loadHub, "repository.getCatalogOptional(")
		assertContains(loadHub, "OptionalIntegrationResult.Stale")
		assertFalse(
			".getOrNull()" in loadHub,
			"Live Bindery hub rows must preserve typed failures instead of dropping them."
		)
	}
}
