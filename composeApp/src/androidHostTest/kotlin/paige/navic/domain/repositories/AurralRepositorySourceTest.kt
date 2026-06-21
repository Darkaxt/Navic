package paige.navic.domain.repositories

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class AurralRepositorySourceTest {
	@Test
	fun confirmationWorkerAndSupportHelpersLiveOutsideRepositoryImplementation() {
		val repository = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt")
		val confirmationManager =
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralConfirmationQueueManager.kt")
		val support = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepositorySupport.kt")
		val confirmationText = confirmationManager.readText()
		val supportText = support.readText()

		assertTrue(
			repository.readLines().size < 1_200,
			"AurralRepository should not own confirmation worker state and cache-key helper models."
		)
		assertContains(confirmationText, "internal class AurralConfirmationQueueManager")
		assertContains(confirmationText, "private val confirmationJobs = mutableMapOf<String, Job>()")
		assertContains(confirmationText, "fun startArtistMonitoringConfirmationWorker(")
		assertContains(supportText, "internal data class AurralLibraryArtistsCacheEntry")
		assertContains(supportText, "internal fun aurralLibraryArtistsCacheKey(")
		assertContains(supportText, "internal fun String?.normalizedAurralSearchName(): String?")
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull { it.isFile }
			?: error("Could not locate $path")
}
