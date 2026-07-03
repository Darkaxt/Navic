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
			"audioSeekTarget = step.whispersyncAudioSeekTarget ?: coordinator.controller.state.whispersync.audioSeekTarget",
			message = "ReaderScreen must persist companion progress with the exact seek target carried by the current coordinator step before falling back to controller state."
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
			"coordinator.reportWhispersyncLoadFailure(",
			message = "Whispersync sidecar or paired-audiobook load failures must surface through controller-owned native status, not only logs."
		)
	}

	@Test
	fun readerScreenLogsWhispersyncActiveSegmentDiagnosticsBeforeRuntimeOverlayEvidence() {
		val readerScreen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
			.readText()

		assertContains(
			readerScreen,
			"step.whispersyncActiveSegment?.let",
			message = "ReaderScreen must log the active Whispersync segment carried by the controller step instead of relying only on bridge overlay callbacks."
		)
		assertContains(
			readerScreen,
			"Whispersync activeSegment",
			message = "The active segment log label is part of the ADB validation contract."
		)
		assertContains(
			readerScreen,
			"ApplyMediaOverlay=\${segment.applyMediaOverlay}",
			message = "The active segment log must expose the literal ApplyMediaOverlay marker expected by ADB validation."
		)
	}

	@Test
	fun readerScreenLogsVisibleTextPreseekBeforeWhispersyncPlayDispatch() {
		val readerScreen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
			.readText()
		val script = sourceFile("scripts/adb-whispersync-enjoyment.ps1")
			.readText()

		assertContains(
			readerScreen,
			"Whispersync play preseek",
			message = "ReaderScreen must log that Play seeks from the current visible EPUB text range before starting audiobook playback."
		)
		assertContains(
			readerScreen,
			"visibleTextRange",
			message = "The Play preseek diagnostic must be tied to the controller's current visible text range, not only later overlay callbacks."
		)
		assertContains(
			script,
			"Whispersync play preseek",
			message = "The ADB enjoyment gate must require the Play preseek diagnostic before claiming sentence/audio startup sync."
		)
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
}
