package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class ReaderWhispersyncCompanionProgressSourceTest {
	@Test
	fun readerScreenPersistsCompanionProgressWithLatestWhispersyncAudioTarget() {
		val readerScreen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
			.readText()

		assertContains(
			readerScreen,
			"audioSeekTarget = coordinator.controller.state.whispersync.audioSeekTarget",
			message = "ReaderScreen must persist companion progress with the latest controller-owned Whispersync audio target, not only the ebook fraction."
		)
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
}
