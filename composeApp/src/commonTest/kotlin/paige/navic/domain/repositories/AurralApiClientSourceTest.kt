package paige.navic.domain.repositories

import java.io.File
import kotlin.test.Test
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
}
