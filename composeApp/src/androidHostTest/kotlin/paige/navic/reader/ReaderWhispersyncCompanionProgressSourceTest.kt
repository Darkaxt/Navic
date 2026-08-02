package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReaderWhispersyncCompanionProgressSourceTest {
	@Test
	fun readerScreenPersistsCompanionProgressOnlyWithDeliveredWhispersyncSeek() {
		val readerScreen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
			.readText()

		assertContains(
			readerScreen,
			"audioSeekTarget = step.whispersyncAudioSeekTarget",
			message = "ReaderScreen must persist companion audio progress only from the exact seek delivered by the current coordinator step."
		)
		assertFalse(
			readerScreen.contains("coordinator.controller.state.whispersync.audioSeekTarget"),
			message = "Pending, unconfirmed overlay seeks must not leak into persisted companion progress."
		)
	}

	@Test
	fun readerScreenLoadsWhispersyncAudiobookPlanWithCompanionAwareResumeProgress() {
		val readerScreen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
			.readText()

		assertContains(
			readerScreen,
			"binderyAudiobookResumeProgressForWhispersyncReader(",
			message = "Whispersync reader sessions must use the same newest direct-or-companion resume policy as the audiobook player, not stale direct audiobook progress only."
		)
	}

	@Test
	fun readerScreenSurfacesWhispersyncLoadFailuresThroughControllerStatus() {
		val readerScreen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
			.readText()

		assertContains(
			readerScreen,
			"coordinator.dispatch { reportWhispersyncLoadFailure(",
			message = "Whispersync sidecar or paired-audiobook load failures must surface through controller-owned native status, not only logs."
		)
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
}
