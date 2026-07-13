package paige.navic.domain.repositories

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AurralApiClientSourceTest {
	@Test
	fun aurralClientDoesNotAbortLongRunningAcquisitionRequestsWithLocalHttpTimeout() {
		val source = File("src/commonMain/kotlin/paige/navic/domain/repositories/AurralApiClient.kt").readText()

		assertFalse(
			"HttpTimeout" in source || "requestTimeoutMillis" in source,
			"Aurral acquisition requests can legitimately take longer than a fixed client timeout; " +
				"the Aurral API client must not abort them with a local Ktor HttpTimeout."
		)
	}

	@Test
	fun fullDiscoveryLoadsIndependentSupplementsInParallelWithoutFalseEmptyFallbacks() {
		val source = File("src/commonMain/kotlin/paige/navic/domain/repositories/AurralApiClient.kt").readText()
		val fetchDiscovery = source.substringAfter("override suspend fun fetchDiscovery(")
			.substringBefore("override suspend fun fetchDiscoveryBase(")

		assertContains(fetchDiscovery, "coroutineScope")
		assertContains(fetchDiscovery, "async")
		assertContains(fetchDiscovery, ".await()")
		assertFalse(
			"getOrDefault(emptyList())" in fetchDiscovery,
			"A failed discovery supplement must not be reported as a successful empty section."
		)
	}
}
