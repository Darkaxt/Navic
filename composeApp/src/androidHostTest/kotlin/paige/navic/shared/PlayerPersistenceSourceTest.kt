package paige.navic.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PlayerPersistenceSourceTest {
	@Test
	fun playbackPersistenceSeparatesImmediateStructureFromSampledProgress() {
		val source = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/shared/MediaPlayer.kt"
		).readText()

		assertContains(source, "distinctUntilChangedBy")
		assertContains(source, "durablePlayerStateKey()")
		assertContains(source, ".sample(5.seconds)")
		assertContains(source, "merge(")
		assertFalse(".debounce(" in source)
	}

	private fun sourceFile(path: String): File = listOf(
		File(path),
		File("../$path")
	).firstOrNull { it.isFile }
		?: error("Unable to locate $path")
}
