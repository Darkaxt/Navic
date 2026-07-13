package paige.navic.domain.repositories

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class DbRepositoryTuningSourceTest {
	@Test
	fun syncConcurrencyAndBatchSizesUseNamedBoundedConstants() {
		val source = sourceFile().readText()

		assertFalse("Semaphore(20)" in source)
		assertFalse("should be enough" in source)
		assertContains(source, "LIBRARY_SYNC_NETWORK_CONCURRENCY")
		assertContains(source, "LIBRARY_DB_WRITE_BATCH_SIZE")
		assertContains(source, "LIBRARY_SONG_ACCUMULATION_LIMIT")
	}

	private fun sourceFile(): File = listOf(
		File("src/commonMain/kotlin/paige/navic/domain/repositories/DbRepository.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/DbRepository.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate DbRepository.kt")
}
