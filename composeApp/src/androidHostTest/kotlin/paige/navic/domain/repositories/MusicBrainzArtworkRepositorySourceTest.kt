package paige.navic.domain.repositories

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class MusicBrainzArtworkRepositorySourceTest {
	@Test
	fun wireAndCacheModelsLiveOutsideRepositoryImplementation() {
		val repository =
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/MusicBrainzArtworkRepository.kt")
		val models =
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/MusicBrainzArtworkModels.kt")
		val modelText = models.readText()

		assertTrue(
			repository.readLines().size < 1_200,
			"MusicBrainzArtworkRepository should not own its serializable cache and wire DTO declarations."
		)
		assertContains(modelText, "internal data class MusicBrainzArtworkCacheStore")
		assertContains(modelText, "data class MusicBrainzTrackMetadata")
		assertContains(modelText, "internal data class CoverArtArchiveResponseDto")
		assertContains(modelText, "internal data class MusicBrainzRecordingDto")
		assertContains(modelText, "@SerialName(\"release-group\")")
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull { it.isFile }
			?: error("Could not locate $path")
}
